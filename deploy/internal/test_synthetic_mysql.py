"""Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. No Docker required."""
import os
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from synthetic_mysql import SyntheticMySQL


class SyntheticMySQLSafetyTest(unittest.TestCase):
    def test_cleanup_without_local_verification_does_nothing(self):
        mysql = SyntheticMySQL()
        mysql.command = Mock()
        mysql.close()
        mysql.command.assert_not_called()

    def test_cleanup_refuses_a_colliding_container_owned_by_someone_else(self):
        mysql = SyntheticMySQL()
        mysql.local_target_verified = True
        mysql.command = Mock(return_value=SimpleNamespace(returncode=0, stdout="ANOTHER_SYNTHETIC_OWNER"))
        mysql.close()
        self.assertEqual(1, mysql.command.call_count)
        self.assertEqual("inspect", mysql.command.call_args.args[0][0])

    def test_cleanup_removes_only_its_exact_owned_container(self):
        mysql = SyntheticMySQL()
        mysql.local_target_verified = True
        mysql.command = Mock(return_value=SimpleNamespace(returncode=0, stdout=mysql.owner + "\n"))
        mysql.close()
        mysql.command.assert_any_call(["rm", "--force", mysql.container], check=False)

    def test_remote_docker_is_refused_before_any_process_and_cleanup(self):
        mysql = SyntheticMySQL()
        mysql.command = Mock()
        with patch.dict(os.environ, {"DOCKER_HOST": "tcp://synthetic.invalid:2376"}):
            with self.assertRaises(RuntimeError):
                mysql.start()
        mysql.close()
        mysql.command.assert_not_called()

    def test_password_is_passed_in_environment_not_arguments(self):
        mysql = SyntheticMySQL()
        with patch("synthetic_mysql.subprocess.run", return_value=SimpleNamespace(returncode=0)) as run:
            mysql.command(["exec", "--env", "MYSQL_PWD", mysql.container, "mysql", "-uroot"])
        args, kwargs = run.call_args
        # Even a failed assertion must not echo the generated credential.
        self.assertTrue(mysql.password not in str(args), "secret appears in command arguments; value omitted")
        self.assertTrue(mysql.password == kwargs["env"]["MYSQL_PWD"], "secret environment handoff mismatch; values omitted")


if __name__ == "__main__":
    unittest.main()
