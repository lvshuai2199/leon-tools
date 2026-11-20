import os
import requests

response = requests.get(
    "https://api.deepseek.com/user/balance",
    headers={"Authorization": f"Bearer {os.environ['DEEPSEEK_API_KEY']}"},
    timeout=60,
)
response.raise_for_status()
print(response.json())