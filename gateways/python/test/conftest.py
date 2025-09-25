import pytest
from fjagepy import Gateway, MessageClass, Performative

DEFAULT_HOST = 'localhost'
DEFAULT_PORT = 5081

TestCompleteNtf = MessageClass('org.arl.fjage.test.TestCompleteNtf');

def pytest_terminal_summary(terminalreporter, exitstatus, config):
    """
    Called after all tests have been collected and run.
    """

    failed_tests = terminalreporter.stats.get('failed', [])

    trace = f"{len(failed_tests)} test(s) failed\n"
    for report in failed_tests:
        trace += f"## {report.nodeid}\n"
        if hasattr(report, 'longrepr'):
            if isinstance(report.longrepr, str):
                trace += report.longrepr + '\n'
            elif hasattr(report.longrepr, 'reprcrash'):
                trace += f"### {report.longrepr.reprcrash.message}\n"
                if hasattr(report.longrepr, 'reprtraceback'):
                    for entry in report.longrepr.reprtraceback.reprentries:
                        trace += f"  File \"{entry.reprfileloc}\" in {entry.name}\n"
                        for line in entry.lines:
                            trace += f"    {line}\n"
                trace += '\n'


    gw = Gateway(hostname=DEFAULT_HOST, port=DEFAULT_PORT)
    msg = TestCompleteNtf();
    msg.recipient = gw.agent('test')
    msg.perf = Performative.INFORM
    msg.status = exitstatus == 0
    msg.trace = trace;
    gw.send(msg);
    gw.close();

# Remove the dot (.) for passed tests in the terminal output
def pytest_report_teststatus(report):
    category, short, verbose = '', '', ''
    if hasattr(report, 'wasxfail'):
        if report.skipped:
            category = 'xfailed'
            verbose = 'xfail'
        elif report.passed:
            category = 'xpassed'
            verbose = ('XPASS', {'yellow': True})
        return (category, short, verbose)
    elif report.when in ('setup', 'teardown'):
        if report.failed:
            category = 'error'
            verbose = 'ERROR'
        elif report.skipped:
            category = 'skipped'
            verbose = 'SKIPPED'
        return (category, short, verbose)
    category = report.outcome
    verbose = category.upper()
    return (category, short, verbose)