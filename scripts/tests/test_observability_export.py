"""Pure stdlib tests: python -m unittest discover -s scripts/tests -v"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import importlib.util
spec = importlib.util.spec_from_file_location('verify_export', Path(__file__).resolve().parents[1] / 'verify-observability-export.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def make_span(name, span_id, parent='', kind='SPAN_KIND_INTERNAL', trace='a'*32, attributes=None, links=None):
    return dict(name=name, traceId=trace, spanId=span_id, parentSpanId=parent,
                kind=kind, attributes=[{'key': k, 'value': {'stringValue': v}} for k, v in (attributes or {}).items()], links=links or [])


def trace_data(*groups):
    return {'batches': [{'resource': {'attributes': [{'key': 'service.name', 'value': {'stringValue': svc}}]},
                        'instrumentationLibrarySpans': [{'spans': spans}]} for svc, spans in groups]}


class ExportValidationTests(unittest.TestCase):
    def test_sensitive_attribute_rejected(self):
        d = trace_data(('agent', [make_span('pg_vector query', '1'*16, attributes={'db.vector.query.content': 'private'})]))
        self.assertTrue(mod.check(d)[0])

    def test_http_parentage(self):
        agent = [make_span('client', '1'*16, kind='SPAN_KIND_CLIENT')]
        mcp = [make_span('server', '2'*16, '1'*16, kind='SPAN_KIND_SERVER'),
               make_span('client', '3'*16, '2'*16, kind='SPAN_KIND_CLIENT')]
        platform = [make_span('server', '4'*16, '3'*16, kind='SPAN_KIND_SERVER')]
        d = trace_data(('sentinelpay-ai-ops-agent', agent), ('sentinelpay-payment-mcp', mcp),
                       ('sentinelpay-payment-platform', platform))
        self.assertEqual(mod.check(d, ['http'])[0], [])
        self.assertTrue(mod.check(trace_data(('sentinelpay-ai-ops-agent', agent)), ['http'])[0])

    def test_kafka_parentage(self):
        producer = make_span('publish', '1'*16, kind='SPAN_KIND_PRODUCER')
        consumer = make_span('process', '2'*16, parent='1'*16, kind='SPAN_KIND_CONSUMER')
        d = trace_data(('payment', [producer, consumer]))
        self.assertEqual(mod.check(d, ['kafka'])[0], [])
        consumer['parentSpanId'] = '3'*16
        self.assertTrue(mod.check(d, ['kafka'])[0])

    def test_outbox_link(self):
        s = make_span('outbox.publish.event', '1'*16, links=[{'traceId':'b'*32, 'spanId':'c'*16}])
        self.assertEqual(mod.check(trace_data(('payment', [s])), ['outbox'])[0], [])
        s['links'] = []
        self.assertTrue(mod.check(trace_data(('payment', [s])), ['outbox'])[0])

    def test_url_warning(self):
        s = make_span('http get', '1'*16, attributes={'http.url':'http://x/internal/ops/payments/0410a2d2-9e53-451a-a9df-b97f253dd95a'})
        errors, warnings = mod.check(trace_data(('payment', [s])))
        self.assertFalse(errors)
        self.assertTrue(warnings)


if __name__ == '__main__':
    unittest.main()
