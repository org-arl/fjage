from typing import Any, Generator
import pytest

from .conftest import DEFAULT_HOST, DEFAULT_PORT

DIRNAME = '.';
FILENAME = 'fjage-test.txt';
TEST_STRING = 'this is a test';
NEW_STRING = 'new test';

from fjagepy import Gateway, Message, ShellExecReq, AgentID, MessageClass, Performative, JSONMessage, GetFileReq, GetFileRsp, PutFileReq

@pytest.fixture(scope="module")
def gateway():
    # Setup code before all tests
    gw = Gateway(hostname=DEFAULT_HOST, port=DEFAULT_PORT)
    shell = gw.agent('shell')
    yield gw
    # Teardown code after all test
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    rsp = gw.request(pfr)
    assert rsp is not None
    gw.close()

@pytest.fixture
def testfile(gateway):
    # Create a new file with the contents of TEST_STRING
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.contents = list(TEST_STRING.encode('utf-8'))
    rsp = shell << pfr
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    yield
    # Teardown code after each test
    pass

def test_gateway_send_shell_exec_req(gateway, testfile):
    """Gateway should be able to send a ShellExecReq."""
    shell = gateway.agent('shell')
    req = ShellExecReq()
    req.recipient = shell
    req.command = 'ps'
    rsp = shell << req
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE

def test_gateway_get_file(gateway, testfile):
    """Gateway should be able to get a file using GetFileReq."""
    shell = gateway.agent('shell')
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp = shell << gfr
    assert rsp is not None
    assert isinstance(rsp, GetFileRsp)
    assert rsp.contents is not None
    assert rsp.contents == list(TEST_STRING.encode('utf-8'))

def test_gateway_get_file_section(gateway, testfile):
    """Gateway should be able to get a section of a file using GetFileReq using offset and length."""
    shell = gateway.agent('shell')
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    gfr.offset = 5
    gfr.length = 4
    rsp = shell << gfr
    assert rsp is not None
    assert isinstance(rsp, GetFileRsp)
    assert rsp.contents is not None
    assert len(rsp.contents) == 4
    assert rsp.offset == 5
    assert rsp.contents == list(TEST_STRING.encode('utf-8')[5:9])

def test_gateway_get_file_section_length_zero(gateway, testfile):
    """Gateway should be able to get a section of a file using GetFileReq using offset and 0 length."""
    shell = gateway.agent('shell')
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    gfr.offset = 9
    gfr.length = 0
    rsp = shell << gfr
    assert rsp is not None
    assert isinstance(rsp, GetFileRsp)
    assert rsp.contents is not None
    assert len(rsp.contents) == len(TEST_STRING) - 9
    assert rsp.offset == 9
    assert rsp.contents == list(TEST_STRING.encode('utf-8')[9:])

def test_gateway_get_file_offset_beyond_length(gateway, testfile):
    """Gateway should refuse to return the contents of the file if offset is beyond file length using GetFileReq."""
    shell = gateway.agent('shell')
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    gfr.offset = 27
    gfr.length = 1
    rsp = shell << gfr
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.REFUSE

def test_gateway_list_files_in_directory(gateway, testfile):
    """Gateway should be able to list all files in a directory using GetFileReq."""
    shell = gateway.agent('shell')
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME
    rsp = shell << gfr
    assert rsp is not None
    assert isinstance(rsp, GetFileRsp)
    content = bytes(rsp.contents)
    lines = content.decode('utf-8').split('\n')
    filenames = [line.split('\t')[0] for line in lines if '\t' in line]
    assert FILENAME in filenames

def test_gateway_delete_file(gateway, testfile):
    """Gateway should be delete files using PutFileReq."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert rsp2.perf is not None
    assert rsp2.perf == Performative.FAILURE

def test_gateway_edit_file_offset_zero(gateway, testfile):
    """Gateway should be able to edit the file correctly using PutFileReq when offset is 0."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.contents = list((TEST_STRING + ' ' + TEST_STRING).encode('utf-8'))
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    assert rsp2.contents == list((TEST_STRING + ' ' + TEST_STRING).encode('utf-8'))

def test_gateway_update_file_content_removed(gateway, testfile):
    """Gateway should be able to update the file correctly using PutFileReq when some content is removed."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.contents = list(TEST_STRING.encode('utf-8')[-4:])
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    assert rsp2.contents == list(TEST_STRING.encode('utf-8')[-4:])

def test_gateway_edit_file_offset_greater_than_zero(gateway, testfile):
    """Gateway should be able to edit the file correctly using PutFileReq when offset greater than 0."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.offset = 10
    pfr.contents = list(TEST_STRING.encode('utf-8'))
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    expected_content = (TEST_STRING[:10] + TEST_STRING).encode('utf-8')
    assert rsp2.contents == list(expected_content)

def test_gateway_edit_file_offset_less_than_zero(gateway, testfile):
    """Gateway should be able to edit the file correctly using PutFileReq when offset less than 0."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.offset = -4
    pfr.contents = list(NEW_STRING.encode('utf-8'))
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    expected_length = len(TEST_STRING) - 4 + len(NEW_STRING)
    assert len(rsp2.contents) == expected_length
    expected_content = (TEST_STRING[:10] + NEW_STRING).encode('utf-8')
    assert rsp2.contents == list(expected_content)

def test_gateway_append_file(gateway, testfile):
    """Gateway should be able to append a file using PutFileReq."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.contents = list((TEST_STRING + ' ' + TEST_STRING).encode('utf-8'))
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    assert rsp2.contents == list((TEST_STRING + ' ' + TEST_STRING).encode('utf-8'))


def test_gateway_save_file_content_removed(gateway, testfile):
    """Gateway should be able to save the file using PutFileReq when some content is removed."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.contents = list(TEST_STRING.encode('utf-8')[-4:])
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    assert rsp2.contents == list(TEST_STRING.encode('utf-8')[-4:])

def test_gateway_append_file_using_offset(gateway, testfile):
    """Gateway should be able to append the file using PutFileReq using offset."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.offset = 10
    pfr.contents = list(TEST_STRING.encode('utf-8'))
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    expected_content = (TEST_STRING[:10] + TEST_STRING).encode('utf-8')
    assert rsp2.contents == list(expected_content)


def test_gateway_append_file_using_offset_less_than_zero(gateway, testfile):
    """Gateway should be able to append the file using PutFileReq using offset less than 0."""
    shell = gateway.agent('shell')
    pfr = PutFileReq()
    pfr.recipient = shell
    pfr.filename = DIRNAME + '/' + FILENAME
    pfr.offset = -4
    pfr.contents = list(NEW_STRING.encode('utf-8'))
    rsp = gateway.request(pfr)
    assert rsp is not None
    assert rsp.perf is not None
    assert rsp.perf == Performative.AGREE
    gfr = GetFileReq()
    gfr.recipient = shell
    gfr.filename = DIRNAME + '/' + FILENAME
    rsp2 = gateway.request(gfr)
    assert rsp2 is not None
    assert isinstance(rsp2, GetFileRsp)
    assert rsp2.contents is not None
    expected_length = len(TEST_STRING) - 4 + len(NEW_STRING)
    assert len(rsp2.contents) == expected_length
    expected_content = (TEST_STRING[:10] + NEW_STRING).encode('utf-8')
    assert rsp2.contents == list(expected_content)