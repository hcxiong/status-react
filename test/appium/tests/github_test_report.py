from hashlib import md5
import hmac


class GithubHtmlReport:
    def __init__(self, test_data, sauce_username, sauce_access_key):
        self.test_data = test_data
        self.sauce_username = sauce_username
        self.sauce_access_key = sauce_access_key
        self.total_test_count = self.get_total_test_count()
        self.total_passed_test_count = self.get_passed_test_count()
        self.total_failed_test_count = self.get_failed_test_count()

    def get_passed_test_count(self):
        count = 0
        for test_info in self.test_data.test_info.items():
            if test_info[1]['error'] is None:
                count += 1
        return count

    def get_failed_test_count(self):
        return self.get_total_test_count() - self.get_passed_test_count()

    def get_total_test_count(self):
        return len(self.test_data.test_info.items())

    def build_html_report(self):
        title = "<h2>%.0f%% of end-end tests have passed</h2>" % (
                    self.total_passed_test_count / self.total_test_count * 100)
        summary = "<code>"
        summary += "Total executed tests: %d<br />" % self.total_test_count
        summary += "Failed tests: %d" % self.total_failed_test_count
        summary += "</code>"
        failed_tests = self.build_failed_tests_table()
        passed_tests = self.build_passed_tests_table()
        report = title + summary + failed_tests + passed_tests
        return report

    def build_passed_tests_table(self):
        tests = "<h3>Passed tests (%d)</h3>" % self.total_passed_test_count
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
        for test_info in self.test_data.test_info.items():
            test_name = test_info[0]
            test_jobs = test_info[1]['jobs']
            test_steps = test_info[1]['steps']
            test_error = test_info[1]['error']
            if not test_error:
                tests += self.build_passed_test_table_row(test_name, test_steps)
        tests += "</tbody>"
        tests += "</table>"
        return tests

    def build_passed_test_table_row(sef, test_name, test_steps, test_jobs=None):
        test_steps_html = str()
        for step in test_steps:
            test_steps_html += "<div>%s</div>" % step

        row = "<tr>"
        row += "<td>%s</td>" % test_name
        row += "<td>"
        row += "<p>"
        row += "<details><summary>Click to expand full logs</summary>"
        row += "<br/>"
        if test_steps_html:
            row += "<p><ins>Test steps:</ins></p>"
            row += "<blockquote>"
            row += "%s" % test_steps_html
            row += "</blockquote>"
            row += "<hr/>"
        row += "<ins>Device sessions:</ins>"
        row += "</details>"
        row += "</p>"
        row += "</td></tr>"
        return row

    def build_failed_tests_table(self):
        tests = "<h3>Failed tests (%d)</h3>" % self.total_failed_test_count
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
        for test_info in self.test_data.test_info.items():
            test_name = test_info[0]
            test_jobs = test_info[1]['jobs']
            test_steps = test_info[1]['steps']
            test_error = test_info[1]['error']
            if test_error:
                tests += self.build_failed_test_table_row(test_name, test_steps)
        tests += "</tbody>"
        tests += "</table>"
        return tests

    def build_failed_test_table_row(self, test_name, test_steps):
        test_steps_html = str()
        for step in test_steps:
            test_steps_html += "<div>%s</div>" % step

        row = "<tr>"
        row += "<td>%s</td>" % test_name
        row += "<td>"
        if test_steps_html:
            row += "<p>"
            row += "<blockquote>"
            row += "%s" % test_steps_html
            row += "</blockquote>"
            row += "</p>"
        row += "</td></tr>"
        return row

    def get_sauce_job_url(self, job_id):
        token = hmac.new(bytes(self.sauce_username + ":" + self.sauce_access_key, 'latin-1'),
                         bytes(job_id, 'latin-1'), md5).hexdigest()
        return "https://saucelabs.com/jobs/%s?auth=%s" % (job_id, token)
