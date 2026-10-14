"""管理后台镜像：静态文件权限不得依赖构建机 umask。"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class AdminImageTest(unittest.TestCase):
    def test_static_files_are_world_readable_regardless_of_build_umask(self):
        text = (ROOT / 'deploy/internal/Dockerfile.admin').read_text()
        copy_at = text.index('dist-internal/ /usr/share/nginx/html/')
        tail = text[copy_at:]
        self.assertRegex(tail, r'find /usr/share/nginx/html -type d -exec chmod 0755')
        self.assertRegex(tail, r'find /usr/share/nginx/html -type f -exec chmod 0644')
        self.assertIsNone(re.search(r'chmod\s+0?7[0-7]7|chmod\s+-R\s+777', text))


if __name__ == '__main__':
    unittest.main()
