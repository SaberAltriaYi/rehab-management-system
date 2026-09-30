import importlib.util
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('materialized_inputs', Path(__file__).with_name('check-materialized-inputs.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MaterializedInputsTest(unittest.TestCase):
    def test_darwin_dataless_flag_and_other_platforms(self):
        self.assertTrue(module.is_dataless(SimpleNamespace(st_flags=0x40008060)))
        self.assertFalse(module.is_dataless(SimpleNamespace(st_flags=0x8040)))
        self.assertFalse(module.is_dataless(SimpleNamespace()))

    def test_local_tree_excludes_generated_cache_and_does_not_follow_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            source.mkdir()
            (source / 'file.ts').write_text('export const value = 1\n')
            (source / 'node_modules').mkdir()
            (source / 'node_modules' / 'ignored.ts').write_text('unused')
            (source / 'external').symlink_to('/nonexistent-p0-synthetic-target')
            result = module.inspect([source], root)
            self.assertEqual(result['status'], 'materialized')
            self.assertEqual(result['checked_entries'], 3)
            self.assertEqual(result['issues'], [])
            result = module.inspect([source], root, include_dependencies=True)
            self.assertEqual(result['checked_entries'], 5)

    def test_missing_required_input_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = module.inspect([root / 'missing'], root)
            self.assertEqual(result['status'], 'blocked')
            self.assertEqual(result['issues'], [{'path': 'missing', 'reason': 'FileNotFoundError'}])

    def test_cloud_directory_is_reported_without_opening_its_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(module, 'is_dataless', return_value=True), patch.object(Path, 'iterdir', side_effect=AssertionError('must not hydrate')):
                result = module.inspect([root], root)
            self.assertEqual(result['status'], 'blocked')
            self.assertEqual(result['checked_entries'], 1)
            self.assertEqual(result['issues'][0]['reason'], 'dataless')


if __name__ == '__main__':
    unittest.main()
