"""Tests fuer tools/import_alienevo.py (ohne AE-Jar: kleine eingebettete Beispiele)."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import import_alienevo as imp  # noqa: E402

PUNCH = """
PalladiumEvents.registerAnimations((event) => {
    event.registerForPower('tetramand/punch', 'alienevo_aliens:tetramand', 200, (builder) => {
        let animation = animationUtil.getAnimationTimerAbilityValue(builder.getPlayer(), 'a', 'punch_righthand', builder.getPartialTicks());
        if (animation > 0) {
            if (!builder.isFirstPerson()) {
                builder.get('right_arm')
                    .setZ(4)
                    .setXRotDegrees(-100)
                    .animate('easeOutCubic', animation);
                builder.get('body')
                    .rotateYDegrees(-45)
                    .animate('easeOutCubic', animation);
            }
            if (builder.isFirstPerson()) {
                builder.get('right_arm')
                    .setY(-10)
                    .animate('easeOutCubic', animation);
            }
        }
        if (abilityUtil.isEnabled(builder.getPlayer(), "alienevo_aliens:tetramand", "punch_lefthand")) {
            builder.get('left_arm').setXRotDegrees(-100).animate('easeOutCubic', animation);
        }
    });
});
PalladiumEvents.registerAnimations((event) => {
    event.registerForPower('kick', 'alienevo_aliens:kineceleran', 15, (builder) => {
        let kick = animationUtil.getAnimationTimerAbilityValue(builder.getPlayer(), 'a', 'kick', builder.getPartialTicks());
        builder.get('body').setXRotDegrees(-57.5 * -1).animate('easeOutSine', kick);
        builder.get('right_leg')
            .setX(-2 - 2.5)
            .rotateX(builder.getModel().rightLeg.xRot * -1)
            .setXRotDegrees(21)
            .animate('easeOutSine', kick);
        builder.get('chest').rotateX(-0.2).animate('easeOutQuad', kick);
    });
});
"""


class ParsePosesTest(unittest.TestCase):
    def setUp(self):
        self.poses = imp.parse_poses_text(PUNCH)

    def test_only_first_timer_belongs_to_pose(self):
        punch = self.poses["tetramand/punch"]
        self.assertIn("right_arm", punch["parts"])
        self.assertNotIn("left_arm", punch["parts"], "zweiter Zeitgeber (linke Hand) darf nicht einfliessen")

    def test_first_person_blocks_are_removed(self):
        ops = self.poses["tetramand/punch"]["parts"]["right_arm"]
        self.assertNotIn(["set_pos", "y", -10.0], ops)
        self.assertIn(["set_pos", "z", 4.0], ops)
        self.assertIn(["set_rot", "x", -100.0], ops)

    def test_ease_names_are_normalised(self):
        self.assertEqual(self.poses["tetramand/punch"]["ease"], "out_cubic")

    def test_constant_expressions_and_skipped_runtime_values(self):
        kick = self.poses["kick"]["parts"]
        self.assertEqual(kick["body"], [["set_rot", "x", 57.5]])
        self.assertEqual(kick["right_leg"], [["set_pos", "x", -4.5], ["set_rot", "x", 21.0]])
        # rotateX im Bogenmass wird zu Grad
        self.assertAlmostEqual(kick["chest"][0][2], -11.4592, places=3)
        self.assertEqual(kick["chest"][0][:2], ["add_rot", "x"])


class HelperTest(unittest.TestCase):
    def test_number(self):
        self.assertEqual(imp._number("-2 - 2.5"), -4.5)
        self.assertEqual(imp._number("(1 + 1) * 3"), 6.0)
        self.assertIsNone(imp._number("builder.getModel().head.xRot"))
        self.assertIsNone(imp._number("__import__('os')"))
        self.assertIsNone(imp._number(""))

    def test_calls_with_nested_parentheses(self):
        calls = imp._calls(".setX(1).rotateX(Math.sin(a(b))).animate('x', t)")
        self.assertEqual([c[0] for c in calls], ["setX", "rotateX", "animate"])
        self.assertEqual(calls[1][1], "Math.sin(a(b))")

    def test_palette_color_including_ext(self):
        palettes = {"alienevo_1_10k_skincolor_palette_1": ["b33637", "902a32"],
                    "alienevo_1_default_skincolor_palette_2_ext": ["917564", "81674c"]}
        self.assertEqual(imp.palette_color(palettes, "1", "pyronite_10k_skincolor_palette_1_color_2"), "902a32")
        self.assertEqual(imp.palette_color(palettes, "1", "pyronite_default_skincolor_palette_2_ext_color_1"), "917564")
        self.assertIsNone(imp.palette_color(palettes, "1", "pyronite_default_skincolor_palette_2_ext_color_9"))
        self.assertIsNone(imp.palette_color(palettes, "1", "unrelated"))

    def test_bone_names(self):
        self.assertEqual(imp.ae_bone_name("armorRightArm"), "right_arm")
        self.assertEqual(imp.ae_bone_name("TAIL_HIGH"), "tail_4")
        self.assertEqual(imp.ae_bone_name("chest"), "ae_chest")
        self.assertEqual(imp.ae_bone_name("MASK"), "MASK")


if __name__ == "__main__":
    unittest.main()
