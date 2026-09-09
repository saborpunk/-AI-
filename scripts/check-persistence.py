"""Real API acceptance. Creates synthetic records and preserves them for restart checks."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
from uuid import uuid4

import httpx

STATE = Path(__file__).resolve().parents[1] / '.tools' / 'persistence-check.json'


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['flow', 'readback', 'database-unavailable', 'python-unavailable'])
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    args = parser.parse_args()
    with httpx.Client(base_url=args.base_url, timeout=12, trust_env=False) as client:
        def call(method, path, expected, payload=None):
            response = client.request(method, '/api/v1/consultations' + path, json=payload)
            assert response.status_code == expected, f'{method} {path}: {response.status_code}, expected {expected}'
            return response.json()

        if args.mode == 'database-unavailable':
            for method, body in [('GET', None), ('POST', {'question': 'synthetic outage check'})]:
                error = call(method, '', 503, body)
                assert error['code'] == 'DATABASE_UNAVAILABLE' and error['requestId']
            print('PASS: real Java returns database-unavailable JSON')
            return
        if args.mode == 'readback':
            saved = json.loads(STATE.read_text(encoding='utf-8'))
            row = call('GET', '/' + saved['id'], 200)
            assert row == saved, 'Persisted content changed across restart'
            print('PASS: original question, draft snapshot, final answer, status and version survive restart')
            return

        row = call('POST', '', 201, {'question': '【合成验收】这个种子发芽率多高？', 'batchCode': 'DEMO-001'})
        path = '/' + row['id']
        assert row['status'] == 'PENDING' and row['version'] == 0 and row['draftResult'] is None
        assert call('GET', path, 200) == row

        if args.mode == 'python-unavailable':
            error = call('POST', path + '/draft', 503, {'version': 0})
            assert error['code'] == 'AI_UNAVAILABLE'
            assert call('GET', path, 200) == row
            result = call('PATCH', path + '/review', 200, {'version': 0, 'action': 'CONFIRM', 'finalAnswer': '【合成验收】人工核实后回复'})
            assert result['status'] == 'CONFIRMED' and result['draftResult'] is None
            print('PASS: failed Python preserves consultation and allows manual confirmation')
            return

        draft = call('POST', path + '/draft', 200, {'version': 0})
        assert draft['status'] == 'DRAFT_READY' and draft['version'] == 1
        assert draft['draftResult']['mode'] == 'mock'
        original_draft = draft['draftResult']
        edited = call('PATCH', path + '/review', 200, {'version': 1, 'action': 'SAVE', 'finalAnswer': '【合成验收】请提供检测记录'})
        assert edited['status'] == 'DRAFT_READY' and edited['version'] == 2
        assert edited['draftResult'] == original_draft
        call('PATCH', path + '/review', 409, {'version': 1, 'action': 'CONFIRM', 'finalAnswer': 'stale'})
        confirmed = call('PATCH', path + '/review', 200, {'version': 2, 'action': 'CONFIRM', 'finalAnswer': edited['finalAnswer']})
        assert confirmed['status'] == 'CONFIRMED' and confirmed['version'] == 3 and confirmed['confirmedAt']
        assert confirmed['draftResult'] == original_draft
        assert call('GET', path, 200) == confirmed
        call('PATCH', path + '/review', 409, {'version': 3, 'action': 'SAVE', 'finalAnswer': 'overwrite'})
        call('POST', path + '/draft', 409, {'version': 3})
        call('GET', '/' + str(uuid4()), 404)
        call('POST', '/' + str(uuid4()) + '/draft', 404, {'version': 0})
        call('GET', '/invalid-id', 400)
        for payload in [{'question': ' '}, {'question': 'a' * 2001}, {'question': 'demo', 'batchCode': '../bad'}]:
            call('POST', '', 400, payload)
        for query in ['?page=0', '?size=101', '?page=1001', '?page=invalid']:
            call('GET', query, 400)
        call('PATCH', path + '/review', 400, {'version': 3, 'action': 'CONFIRM', 'finalAnswer': ' '})
        call('PATCH', path + '/review', 400, {'version': -1, 'action': 'SAVE', 'finalAnswer': 'x'})
        call('PATCH', path + '/review', 400, {'version': 3, 'action': 'INVALID', 'finalAnswer': 'x'})
        history = call('GET', '?page=1&size=20', 200)
        assert any(item['id'] == row['id'] for item in history['items'])
        assert len(history['items']) <= 20
        # Two real HTTP requests race on the same version; exactly one may commit.
        concurrent_row = call('POST', '', 201, {'question': '【合成验收】并发人工修改'})
        concurrent_path = '/api/v1/consultations/' + concurrent_row['id'] + '/review'
        def edit(answer):
            return client.patch(concurrent_path, json={'version': 0, 'action': 'SAVE', 'finalAnswer': answer}).status_code
        with ThreadPoolExecutor(max_workers=2) as executor:
            statuses = list(executor.map(edit, ['【合成验收】回复A', '【合成验收】回复B']))
        assert sorted(statuses) == [200, 409], f'Concurrent writes: {statuses}'
        assert call('GET', '/' + concurrent_row['id'], 200)['version'] == 1
        STATE.parent.mkdir(parents=True, exist_ok=True)
        STATE.write_text(json.dumps(confirmed, ensure_ascii=False, indent=2), encoding='utf-8')
        print('PASS: save, draft persistence, edit, confirm, original snapshot, concurrent conflicts, missing record, invalid data, history')


if __name__ == '__main__':
    run()
