import json
from hashlib import md5
import hmac
import os

from tests import SingleTestData


class GithubHtmlReport:

    TEST_REPORT_DIR = "%s/../report" % os.path.dirname(os.path.abspath(__file__))

    def __init__(self, sauce_username, sauce_access_key):
        self.sauce_username = sauce_username
        self.sauce_access_key = sauce_access_key
        self.init_report()

    def init_report(self):
        if not os.path.exists(self.TEST_REPORT_DIR):
            os.makedirs(self.TEST_REPORT_DIR)
        # delete all old files in report dir
        file_list = [f for f in os.listdir(self.TEST_REPORT_DIR)]
        for f in file_list:
            os.remove(os.path.join(self.TEST_REPORT_DIR, f))

    def get_test_report_file_path(self, test_name):
        file_name = "%s.json" % test_name
        return os.path.join(self.TEST_REPORT_DIR, file_name)

    def build_html_report(self):
        tests = self.get_all_tests()
        passed_tests = self.get_passed_tests()
        failed_tests = self.get_failed_tests()

        if len(tests) > 0:
            title_html = "<h2>%.0f%% of end-end tests have passed</h2>" % (len(passed_tests) / len(tests) * 100)
            summary_html = "<pre>"
            summary_html += "Total executed tests: %d<br/>" % len(tests)
            summary_html += "Failed tests: %d" % len(failed_tests)
            summary_html += "</pre>"
            failed_tests_html = str()
            passed_tests_html = str()
            if failed_tests:
                failed_tests_html = self.build_failed_tests_html_table(failed_tests)
            if passed_tests:
                passed_tests_html = self.build_passed_tests_html_table(passed_tests)
            return title_html + summary_html + failed_tests_html + passed_tests_html
        else:
            return None

    def save_test(self, test):
        file_path = self.get_test_report_file_path(test.name)
        json.dump(test.__dict__, open(file_path, 'w'))

    def get_all_tests(self):
        tests = list()
        file_list = [f for f in os.listdir(self.TEST_REPORT_DIR)]
        for file_name in file_list:
            file_path = os.path.join(self.TEST_REPORT_DIR, file_name)
            test_dict = json.load(open(file_path))
            tests.append(SingleTestData(name=test_dict['name'], steps=test_dict['steps'],
                                        jobs=test_dict['jobs'], error=test_dict['error']))
        return tests

    def get_failed_tests(self):
        tests = self.get_all_tests()
        failed = list()
        for test in tests:
            if test.error is not None:
                failed.append(test)
        return failed

    def get_passed_tests(self):
        tests = self.get_all_tests()
        passed = list()
        for test in tests:
            if test.error is None:
                passed.append(test)
        return passed

    def build_passed_tests_html_table(self, passed_tests):
        tests = "<h3>Passed tests (%d)</h3>" % len(passed_tests)
        tests += "<details>"
        tests += "<summary>Click to expand</summary>"
        tests += "<br/>"
        tests += "<table style=\"width: 100%\">"
        tests += "<colgroup>"
        tests += "<col span=\"1\" style=\"width: 20%;\">"
        tests += "<col span=\"1\" style=\"width: 80%;\">"
        tests += "</colgroup>"
        tests += "<tbody>"
        tests += "<tr>"
        tests += "<th>Test</th>"
        tests += "<th>Logs</th>"
        tests += "</tr>"
        for test in passed_tests:
            tests += self.build_passed_test_html_table_row(test)
        tests += "</tbody>"
        tests += "</table>"
        tests += "</details>"
        return tests

    def build_passed_test_html_table_row(self, passed_test):
        test_steps_html = str()
        for step in passed_test.steps:
            test_steps_html += "<div>%s</div>" % step

        row = "<tr>"
        row += "<td>%s</td>" % passed_test.name
        row += "<td>"
        row += "<p>"
        row += "<details><summary>Click to expand full logs</summary>"
        row += "<br/>"
        if test_steps_html:
            row += "<p><ins>Test steps:</ins></p>"
            row += "<blockquote>"
            row += "%s" % test_steps_html
            row += "</blockquote>"
        if passed_test.jobs:
            row += "<hr/>"
            row += self.build_device_sessions_html(passed_test.jobs)

        row += "</details>"
        row += "</p>"
        row += "</td></tr>"
        return row

    def build_failed_tests_html_table(self, failed_tests):
        tests = "<h3>Failed tests (%d)</h3>" % len(failed_tests)
        tests += "<table style=\"width: 100%\">"
        tests += "<colgroup>"
        tests += "<col span=\"1\" style=\"width: 20%;\">"
        tests += "<col span=\"1\" style=\"width: 80%;\">"
        tests += "</colgroup>"
        tests += "<tbody>"
        tests += "<tr>"
        tests += "<th>Test</th>"
        tests += "<th>Failure</th>"
        tests += "</tr>"
        for test in failed_tests:
            tests += self.build_failed_test_html_table_row(test)
        tests += "</tbody>"
        tests += "</table>"
        return tests

    def build_failed_test_html_table_row(self, failed_test):
        test_steps_html = list()
        for step in failed_test.steps:
            test_steps_html.append("<div>%s</div>" % step)
        row = "<tr>"
        row += "<td>%s</td>" % failed_test.name
        row += "<td>"
        if test_steps_html:
            row += "<p>"
            row += "<blockquote>"
            # last 3 steps as summary
            row += "%s" % ''.join(test_steps_html[-3:])
            row += "</blockquote>"
            row += "</p>"
            row += "<p>"
            row += "<details><summary>Click to expand full logs</summary>"
            row += "<br/>"
            row += "<p><ins>Test steps:</ins></p>"
            row += "<blockquote>"
            row += "%s" % ''.join(test_steps_html)
            row += "</blockquote>"
        if failed_test.error:
            row += "<code>%s</code>" % failed_test.error
        if failed_test.jobs:
            row += "<hr/>"
            row += self.build_device_sessions_html(failed_test.jobs)
        if test_steps_html:
            row += "</details>"
            row += "</p>"
        row += "</td></tr>"
        return row

    def get_sauce_job_url(self, job_id):
        token = hmac.new(bytes(self.sauce_username + ":" + self.sauce_access_key, 'latin-1'),
                         bytes(job_id, 'latin-1'), md5).hexdigest()
        return "https://saucelabs.com/jobs/%s?auth=%s" % (job_id, token)

    def build_device_sessions_html(self, jobs):
        html = "<ins>Device sessions:</ins>"
        html += "<p><ul>"
        for job_id in jobs:
            html += "<li><a href=\"%s\"></a></li>" % self.get_sauce_job_url(job_id)
        html += "</p></ul>"
        return html
