#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json
from pathlib import Path
import tempfile
import unittest

from trajectory import ORBIT_PITCH_MAX, ORBIT_PITCH_MIN, keep_sample, orbit_view


def point(x, y=0, z=0):
    return dict(x_mm=x, y_mm=y, z_mm=z)


class DistanceSamplingTest(unittest.TestCase):
    def test_first_and_distance_boundaries(self):
        self.assertTrue(keep_sample(None, point(0), 1, strict=True))
        for value in (0, 0.5, 1):
            self.assertFalse(keep_sample(point(0), point(value), 1, strict=True))
        self.assertTrue(keep_sample(point(0), point(1.001), 1, strict=True))

    def test_xyz_euclidean_distance(self):
        self.assertFalse(keep_sample(point(0), point(3, 4, 0), 5, strict=True))
        self.assertTrue(keep_sample(point(0), point(3, 4, 1), 5, strict=True))
        self.assertTrue(keep_sample(point(0), point(-6), 5, strict=True))

    def test_reference_is_last_recorded_not_last_received(self):
        kept = []
        for value in (0, 0.6, 1.1, 1.6, 2.2):
            sample = point(value)
            if keep_sample(kept[-1] if kept else None, sample, 1, strict=True):
                kept.append(sample)
        self.assertEqual([p['x_mm'] for p in kept], [0, 1.1, 2.2])

    def test_invalid_current_is_not_recorded(self):
        for sample in ({}, point(None), point(float('nan')), point(float('inf'))):
            self.assertFalse(keep_sample(None, sample, 1, strict=True))
            self.assertFalse(keep_sample(point(0), sample, 1, strict=True))

    def test_invalid_old_reference_reseeds_after_enabling(self):
        self.assertTrue(keep_sample({}, point(0), 1, strict=True))

    def test_disabled_and_legacy_semantics_unchanged(self):
        self.assertTrue(keep_sample(point(0), point(0), 0))
        self.assertTrue(keep_sample(point(0), point(1), 1))


class OrbitViewTest(unittest.TestCase):
    def test_horizontal_drag_keeps_yaw_direction(self):
        for pitch in (0.0, 0.5, 1.2, -0.8):
            yaw, out_pitch = orbit_view(0.0, pitch, 10, 0)
            self.assertGreater(yaw, 0.0)
            self.assertGreaterEqual(out_pitch, ORBIT_PITCH_MIN)

    def test_upward_drag_continues_past_overhead(self):
        yaw, pitch = orbit_view(0.75, 0.05, 0, -100)
        self.assertEqual(yaw, 0.75)
        self.assertAlmostEqual(pitch, -0.75)

    def test_pitch_stops_at_lower_limit(self):
        yaw, pitch = orbit_view(0.75, -1.3, 0, -100)
        self.assertEqual(yaw, 0.75)
        self.assertEqual(pitch, ORBIT_PITCH_MIN)

    def test_pitch_stops_at_top(self):
        yaw, pitch = orbit_view(0.75, 1.3, 0, 100)
        self.assertEqual(yaw, 0.75)
        self.assertEqual(pitch, ORBIT_PITCH_MAX)

    def test_crossing_horizon_does_not_invert_yaw(self):
        yaw, pitch = orbit_view(1.0, 0.01, 5, -20)
        self.assertGreater(yaw, 1.0)
        self.assertGreaterEqual(pitch, ORBIT_PITCH_MIN)


try:
    import elite_script_send_qt as ui
    from PySide6.QtWidgets import QApplication
except ImportError:
    ui = None


@unittest.skipIf(ui is None, 'PySide6 UI not installed')
class DistanceSwitchUiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.old_path, self.old_dir = ui.SETTINGS_PATH, ui.app_dir
        ui.SETTINGS_PATH = self.root / 'last_settings.json'
        ui.app_dir = lambda: self.root
        self.windows = []

    def tearDown(self):
        for window in self.windows:
            window.close()
            window.deleteLater()
        self.app.processEvents()
        ui.SETTINGS_PATH, ui.app_dir = self.old_path, self.old_dir
        self.temp.cleanup()

    def make_window(self, settings=None):
        if settings is not None:
            ui.SETTINGS_PATH.write_text(json.dumps(settings), encoding='utf-8')
        window = ui.EliteScriptWindow()
        window._boot_after_show_done = True
        self.windows.append(window)
        return window

    def test_toggle_persistence_and_live_filter(self):
        window = self.make_window({})
        self.assertFalse(window.chk_distance_filter.isChecked())
        self.assertFalse(window.sp_step.isEnabled())
        self.assertTrue(window._should_record_sample(point(0), point(0)))
        window.chk_distance_filter.setChecked(True)
        window.sp_step.setValue(2.5)
        self.assertTrue(window.sp_step.isEnabled())
        self.assertFalse(window._should_record_sample(point(0), point(2.5)))
        self.assertTrue(window._should_record_sample(point(0), point(2.51)))
        window.chk_distance_filter.setChecked(False)
        self.assertEqual(window.sp_step.value(), 2.5)
        self.assertTrue(window._should_record_sample(point(0), point(0)))
        window._save_now()
        restored = self.make_window()
        self.assertFalse(restored.chk_distance_filter.isChecked())
        self.assertEqual(restored.sp_step.value(), 2.5)
        restored.chk_distance_filter.setChecked(True)
        restored._save_now()
        enabled = self.make_window()
        self.assertTrue(enabled.chk_distance_filter.isChecked())
        self.assertEqual(enabled.sp_step.value(), 2.5)

    def test_legacy_nonzero_threshold_stays_enabled(self):
        window = self.make_window({'sample_step_mm': 3.25})
        self.assertTrue(window.chk_distance_filter.isChecked())
        self.assertEqual(window.sp_step.value(), 3.25)

    def test_legacy_zero_stays_unfiltered(self):
        window = self.make_window({'sample_step_mm': 0})
        self.assertFalse(window.chk_distance_filter.isChecked())
        self.assertFalse(window.sp_step.isEnabled())

    def test_clear_traj_restarts_recording(self):
        window = self.make_window({})
        window._recording = True
        with window._rec_lock:
            window._rec_samples = [point(0), point(10)]
            window._rec_t0 = 1.0
        window._clear_current_traj()
        self.assertTrue(window._recording)
        with window._rec_lock:
            self.assertEqual(window._rec_samples, [])
            self.assertGreater(window._rec_t0, 1.0)
        self.assertEqual(window.lbl_rec.text(), "记录中  0 点")
        self.assertEqual(window.btn_record.text(), "停止记录")

    def test_clear_traj_without_recording_stays_stopped(self):
        window = self.make_window({})
        with window._rec_lock:
            window._rec_samples = [point(0)]
            window._rec_t0 = 1.0
        window._clear_current_traj()
        self.assertFalse(window._recording)
        with window._rec_lock:
            self.assertEqual(window._rec_samples, [])
            self.assertIsNone(window._rec_t0)
        self.assertEqual(window.lbl_rec.text(), "")


if __name__ == '__main__':
    unittest.main()