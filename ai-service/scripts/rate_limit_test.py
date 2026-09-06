"""并发压测网关限流：15 个并发请求 /api/ai/writing-assist，预期部分 429"""
import concurrent.futures
import urllib.request

BASE = "http://localhost:9000"


def get_token():
    import json
    import time

    u = f"e2e{int(time.time())}"
    req = urllib.request.Request(
        f"{BASE}/api/auth/register",
        data=json.dumps({"username": u, "password": "Test123456", "email": f"{u}@test.com"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    urllib.request.urlopen(req, timeout=15).read()
    req = urllib.request.Request(
        f"{BASE}/api/auth/login",
        data=json.dumps({"username": u, "password": "Test123456"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    body = json.loads(urllib.request.urlopen(req, timeout=15).read())
    return body["data"]["accessToken"]


def hit(token, i):
    req = urllib.request.Request(
        f"{BASE}/api/ai/writing-assist",
        data=b'{"type":"outline","content":"test"}',
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
        method="POST",
    )
    try:
        resp = urllib.request.urlopen(req, timeout=30)
        return resp.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception as e:
        return f"ERR:{e}"


if __name__ == "__main__":
    token = get_token()
    with concurrent.futures.ThreadPoolExecutor(max_workers=15) as ex:
        results = [f.result() for f in [ex.submit(hit, token, i) for i in range(15)]]
    counts = {}
    for r in results:
        counts[r] = counts.get(r, 0) + 1
    print("并发15次结果分布:", counts)
    print("限流生效" if counts.get(429, 0) > 0 else "未触发限流")
