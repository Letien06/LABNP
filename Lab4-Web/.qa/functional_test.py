"""Real HTTP + real JavaFX controller checks; all services bind only loopback."""
import hashlib
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
from contextlib import nullcontext
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import web_service as web

def check(ok, label):
    assert ok, label
    print('PASS ' + label, flush=True)

runtime = Path('.qa') / ('http-run-' + uuid.uuid4().hex[:8]); runtime.mkdir()
with nullcontext(runtime) as tmp:
    # Keep uploaded test data separate from the student's public/downloaded files.
    web.DOWNLOADS = Path(tmp) / 'server-files'; web.DOWNLOADS.mkdir()
    history = Path(tmp) / 'history.json'
    client = web.WebClient(history)
    server = web.LabWebServer(port=0); server.start()
    base = 'http://%s:%s' % server.address
    try:
        code, headers, body = client.get(base + '/')
        check(code == 200 and b'Lab4 Web services' in body, 'GET HTML service')
        code, headers, body = client.get(base + '/api/time')
        check(code == 200 and json.loads(body)['service'] == 'time', 'GET JSON time service')
        code, heads, empty = client.head(base + '/download/sample.txt')
        check(code == 200 and not empty and int(heads['Content-Length']) == web.SAMPLE.stat().st_size, 'HEAD headers and empty response body')
        code, headers, body = client.post(base + '/api/echo', {'lab':4, 'message':'Kiểm tra HTTP'})
        check(code == 200 and json.loads(body)['payload']['lab'] == 4, 'POST JSON echo')
        payload = bytes(range(256)) * 1024 + b'Lab4 binary upload'
        code, headers, body = client.post(base + '/api/upload?name=functional.bin', payload)
        check(code == 201 and json.loads(body)['bytes'] == len(payload), 'POST binary upload')
        copy = client.download(base + '/download/functional.bin', Path(tmp) / 'copy.bin')
        check(hashlib.sha256(copy.read_bytes()).digest() == hashlib.sha256(payload).digest(), 'Download binary SHA-256 matches uploaded bytes')
        check(client.get(base + '/missing')[0] == 404, 'Missing route returns HTTP 404')
        check(client.request('POST', base + '/api/echo', '{bad json', {'Content-Type':'application/json'})[0] == 400, 'Malformed JSON returns HTTP 400')
        check(len(web.WebClient(history).history()) == 8, 'Request history persists and reopens')
        print('Launching actual JavaFX controller tests against ' + base, flush=True)
        java_home = os.environ.get('JAVA_HOME')
        java = str(Path(java_home) / 'bin' / 'java.exe') if java_home else shutil.which('java')
        if not java:
            raise RuntimeError('Set JAVA_HOME or add Java to PATH before running the JavaFX checks.')
        fx = ';'.join(str(p) for p in (Path.home()/'.m2/repository/org/openjfx').glob('*/21/*-win.jar'))
        subprocess.run([str(java), '--module-path',fx,'--add-modules','javafx.controls,javafx.fxml,javafx.web',
            '-Dlab4.home='+base+'/', '-cp','.qa;target/classes','BrowserFunctionalTest'], check=True, timeout=120)
        print('Lab4 complete functional test PASS', flush=True)
    finally:
        server.close()
