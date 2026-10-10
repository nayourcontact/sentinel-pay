#!/usr/bin/env python3
"""Validate Tempo JSON exports offline; returns non-zero on explicit validation failures.

Usage: python scripts/verify-observability-export.py <export.json> [--require http|kafka|outbox]
Multiple --require flags may be supplied; different exported traces can be checked separately.
"""
import argparse
import json
import re
from pathlib import Path

SENSITIVE = {
    'db.vector.query.content', 'db.vector.query.filter', 'db.vector.query.response.documents',
    'gen_ai.prompt', 'gen_ai.completion', 'spring.ai.chat.client.input',
    'spring.ai.chat.client.output', 'spring.ai.tool.call.arguments',
    'spring.ai.tool.call.result', 'spring.ai.model.request.messages',
    'spring.ai.model.response.messages',
}
REDACTABLE_URI = re.compile(r'/internal/ops/payments/[0-9a-f]{8}-[0-9a-f-]{27,}', re.I)


def extract(data):
    result = []
    for batch in data.get('batches', []):
        resource = {a['key']: a.get('value', {}).get('stringValue', '')
                    for a in batch.get('resource', {}).get('attributes', [])}
        for library in batch.get('instrumentationLibrarySpans', []):
            for span in library.get('spans', []):
                result.append((resource.get('service.name', ''), span))
    return result


def check(data, requirements=()):
    entries = extract(data)
    errors = []
    warnings = []
    if not entries:
        return ['No spans in Tempo export'], []
    by_id = {(s.get('traceId'), s.get('spanId')): (svc, s) for svc, s in entries}
    for svc, s in entries:
        attrs = {a['key']: a.get('value', {}).get('stringValue', '') for a in s.get('attributes', [])}
        for key in SENSITIVE.intersection(attrs):
            errors.append(f'Sensitive attribute exported: {svc} {s.get("name")} {key}')
        for key in ('http.url', 'uri', 'url.full'):
            if REDACTABLE_URI.search(str(attrs.get(key, ''))):
                warnings.append(f'Raw payment ID in {key}: {svc} {s.get("name")}')
        parent = s.get('parentSpanId')
        if parent and (s.get('traceId'), parent) not in by_id:
            warnings.append(f'Parent span not present in export: {s.get("name")} ({parent})')
    if 'http' in requirements:
        pairs = [('sentinelpay-ai-ops-agent', 'sentinelpay-payment-mcp'),
                 ('sentinelpay-payment-mcp', 'sentinelpay-payment-platform')]
        for src, target in pairs:
            found = any(svc == target and s.get('kind') == 'SPAN_KIND_SERVER'
                        and (s.get('traceId'), s.get('parentSpanId')) in by_id
                        and by_id[(s['traceId'], s['parentSpanId'])][0] == src
                        and by_id[(s['traceId'], s['parentSpanId'])][1].get('kind') == 'SPAN_KIND_CLIENT'
                        for svc, s in entries)
            if not found:
                errors.append(f'Missing HTTP propagation {src} -> {target}')
    if 'kafka' in requirements:
        producers = [(svc, s) for svc, s in entries if s.get('kind') == 'SPAN_KIND_PRODUCER']
        consumers = [(svc, s) for svc, s in entries if s.get('kind') == 'SPAN_KIND_CONSUMER']
        if not producers or not consumers:
            errors.append('Kafka producer or consumer span missing')
        for _, consumer in consumers:
            key = (consumer.get('traceId'), consumer.get('parentSpanId'))
            if key not in by_id or by_id[key][1].get('kind') != 'SPAN_KIND_PRODUCER':
                errors.append(f'Kafka consumer not parented by producer: {consumer.get("name")}')
    if 'outbox' in requirements:
        linked = [(svc, s) for svc, s in entries
                  if s.get('name') == 'outbox.publish.event' and s.get('links')]
        if not linked:
            errors.append('Outbox publish span with Span Link missing')
        for _, span in linked:
            for link in span.get('links', []):
                if not re.fullmatch('[0-9a-f]{32}', link.get('traceId', '')) or not re.fullmatch('[0-9a-f]{16}', link.get('spanId', '')):
                    errors.append('Outbox Span Link has invalid trace/span IDs')
    return errors, list(dict.fromkeys(warnings))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('filename', type=Path)
    parser.add_argument('--require', action='append', choices=['http', 'kafka', 'outbox'], default=[])
    args = parser.parse_args()
    try:
        data = json.loads(args.filename.read_text(encoding='utf-8-sig'))
    except (OSError, ValueError) as exc:
        print(f'FAIL: Cannot open/parse export: {exc}')
        return 2
    entries = extract(data)
    errors, warnings = check(data, args.require)
    print(f'Analyzed {len(entries)} spans / {len({s.get("traceId") for _,s in entries})} trace(s)')
    for msg in warnings:
        print('WARN:', msg)
    for msg in errors:
        print('FAIL:', msg)
    if errors:
        return 1
    print('PASS: targeted privacy checks' + (f' and {", ".join(args.require)} propagation checks' if args.require else ''))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
