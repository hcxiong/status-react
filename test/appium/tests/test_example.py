import pytest

from tests import test_data, info


@pytest.mark.lukasz
class TestWallet:

    def setup_method(self, method):
        test_data.test_info[test_data.test_name] = dict()
        test_data.test_info[test_data.test_name]['jobs'] = list()
        test_data.test_info[test_data.test_name]['steps'] = list()
        test_data.test_info[test_data.test_name]['error'] = None

    @pytest.mark.wallet
    def test_fail(self):
        info('Transaction is found in Ropsten network')
        info('Transaction is found in Ropsten network')
        assert 1 == 2

    @pytest.mark.wallet
    def test_success(self):
        info('Transaction is found in Ropsten network')
        info('Transaction is found in Ropsten network')


# class TestWallet(SingleDeviceTestCase):
#
#     @pytest.mark.lukasz
#     def test_wallet_error_messages(self):
#         console = ConsoleView(self.driver)
#         console.create_user()
#         console.back_button.click()
#         wallet_view = console.wallet_button.click()
#         send_transaction = wallet_view.send_button.click()
#         send_transaction.amount_edit_box.send_keys('asd')
#         send_transaction.find_full_text('Amount is valid number')
#         send_transaction.amount_edit_box.send_keys('0,1')
#         send_transaction.find_full_text('Insufficient funds')
