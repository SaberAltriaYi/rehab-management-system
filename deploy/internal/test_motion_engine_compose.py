#!/usr/bin/env python3
"""Compose contract for the optional motion-engine service (no Docker required).

python3 -m unittest discover -s deploy/internal -p 'test_motion_engine_compose.py' -v
"""
import re
import unittest
from pathlib import Path

DEPLOY = Path(__file__).resolve().parent


def service_block(text, name):
    m = re.search(r'^  %s:\n((?:    .*\n|\s*\n)+)' % re.escape(name), text, re.M)
    if not m:
        raise AssertionError('service %s not found' % name)
    return m.group(1)


class MotionEngineComposeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compose = (DEPLOY / 'docker-compose.yml').read_text(encoding='utf-8')
        cls.block = service_block(cls.compose, 'motion-engine')

    def test_entrypoint_overrides_research_cli(self):
        # 镜像 ENTRYPOINT 是 python -m rehab_biomechanics；只写 command 会拼成错误命令
        self.assertIn('entrypoint: ["python", "-m", "rehab_biomechanics.motion.service"]', self.block)
        self.assertIn('command: ["--host", "0.0.0.0", "--port", "8790"]', self.block)

    def test_opt_in_profile_and_hardening(self):
        self.assertIn('profiles: ["motion"]', self.block)
        self.assertIn('read_only: true', self.block)
        self.assertIn('no-new-privileges:true', self.block)
        self.assertRegex(self.block, r'cap_drop:\n\s+- ALL')
        self.assertIn('/tmp:size=', self.block)

    def test_not_published_to_host(self):
        self.assertNotRegex(self.block, r'^\s+ports:', 'motion-engine 只允许 Compose 内网访问')

    def test_token_only_from_env(self):
        self.assertIn('MOTION_ENGINE_TOKEN: ${MOTION_ENGINE_TOKEN:-}', self.block)
        server = service_block(self.compose, 'server')
        self.assertIn('MOTION_ENGINE_URL: ${MOTION_ENGINE_URL:-}', server)
        self.assertIn('MOTION_ENGINE_TOKEN: ${MOTION_ENGINE_TOKEN:-}', server)
        self.assertNotIn('OPENCAP_API_TOKEN', server, 'OpenCap 凭据只由引擎持有')

    def test_ai_analysis_stays_disabled_by_default(self):
        self.assertIn('OPENAI_ENABLE_AI_ANALYSIS: "false"', self.compose)

    def test_healthcheck_uses_unauthenticated_health_endpoint(self):
        self.assertIn('/internal/v1/health', self.block)


if __name__ == '__main__':
    unittest.main()
