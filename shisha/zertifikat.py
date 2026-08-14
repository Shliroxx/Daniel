"""Selbst ausgestelltes HTTPS-Zertifikat fuer den Zugriff vom Handy.

Safari gibt die Kamera nur in einem sicheren Kontext frei. Ueber `localhost` gilt
das automatisch — vom iPhone aus rufst du den Rechner aber ueber seine IP im WLAN
auf, und dann braucht es HTTPS. Deshalb stellen wir uns hier selbst ein Zertifikat
aus. Beim ersten Aufruf warnt Safari einmal; nach "Trotzdem fortfahren" laeuft es.
"""

from __future__ import annotations

import datetime as dt
import ipaddress
import logging
import socket
from pathlib import Path

from jarvis.config import config

log = logging.getLogger("shisha.zertifikat")

GUELTIG_TAGE = 397  # laenger akzeptiert Safari nicht


def lokale_ips() -> list[str]:
    """Alle IPv4-Adressen, unter denen der Rechner im Netz erreichbar sein duerfte."""
    adressen = {"127.0.0.1"}
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            adressen.add(sock.getsockname()[0])
    except OSError:
        pass
    try:
        for eintrag in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            adressen.add(eintrag[4][0])
    except OSError:
        pass
    return sorted(adressen)


def zertifikat_besorgen(erneuern: bool = False) -> tuple[Path, Path]:
    """Liefert (Zertifikat, Schluessel) und legt beides an, falls noetig."""
    verzeichnis = config.certs_dir
    verzeichnis.mkdir(parents=True, exist_ok=True)
    cert_pfad = verzeichnis / "shisha-cert.pem"
    key_pfad = verzeichnis / "shisha-key.pem"

    if cert_pfad.exists() and key_pfad.exists() and not erneuern:
        if not _abgelaufen(cert_pfad):
            return cert_pfad, key_pfad
        log.info("Zertifikat abgelaufen — stelle ein neues aus.")

    try:
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.x509.oid import NameOID
    except ImportError as exc:
        raise RuntimeError(
            "Fuer HTTPS fehlt das Paket 'cryptography'.\n"
            "Installiere es mit:  pip install cryptography\n"
            "Oder setz in der .env SHISHA_TLS=false — dann laeuft die Kamera nur "
            "ueber localhost oder einen Tunnel."
        ) from exc

    schluessel = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Shisha-Coach")])

    alternativen: list[x509.GeneralName] = [
        x509.DNSName("localhost"),
        x509.DNSName(socket.gethostname()),
        x509.DNSName(f"{socket.gethostname()}.local"),
    ]
    for adresse in lokale_ips():
        try:
            alternativen.append(x509.IPAddress(ipaddress.ip_address(adresse)))
        except ValueError:
            continue

    jetzt = dt.datetime.now(dt.timezone.utc)
    zertifikat = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(schluessel.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(jetzt - dt.timedelta(days=1))
        .not_valid_after(jetzt + dt.timedelta(days=GUELTIG_TAGE))
        .add_extension(x509.SubjectAlternativeName(alternativen), critical=False)
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .sign(schluessel, hashes.SHA256())
    )

    cert_pfad.write_bytes(zertifikat.public_bytes(serialization.Encoding.PEM))
    key_pfad.write_bytes(
        schluessel.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        )
    )
    key_pfad.chmod(0o600)
    log.info("Neues HTTPS-Zertifikat abgelegt: %s", cert_pfad)
    return cert_pfad, key_pfad


def _abgelaufen(cert_pfad: Path) -> bool:
    try:
        from cryptography import x509

        zertifikat = x509.load_pem_x509_certificate(cert_pfad.read_bytes())
        # not_valid_after_utc gibt es erst ab cryptography 42.
        ende = getattr(zertifikat, "not_valid_after_utc", None)
        if ende is None:
            ende = zertifikat.not_valid_after.replace(tzinfo=dt.timezone.utc)
        return ende <= dt.datetime.now(dt.timezone.utc)
    except (ImportError, ValueError, OSError) as exc:
        # Unlesbar oder Paket fehlt — dann lieber neu ausstellen. Aber sagen,
        # warum: sonst stellt der Server bei jedem Start still ein neues aus,
        # und Safari verlangt jedes Mal erneut das Vertrauen.
        log.warning("Zertifikat nicht lesbar (%s) — es wird neu ausgestellt.", exc)
        return True
