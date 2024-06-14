import requests
import json

def rest_test():
    # 서버 주소 및 포트 설정
    server_url = 'http://localhost:8501/v1/models/my_model:predict'
    # EC2
    # server_url = 'http://ec2ip주소환경변수로주입하자:8501/v1/models/my_model:predict'

    # 입력 데이터 설정
    data = {
        "signature_name": "serving_default",
        "instances": [{"input": [[1171, 1], [467, 1], [1703, 1], [1817, 1], [1698, 1], [623, 0], [1182, 0], [1614, 0], [396, 0],
               [1681, 0], [1564, 1], [461, 1], [782, 1], [593, 1], [1582, 1], [774, 0], [1660, 0], [1583, 0], [790, 0],
               [1531, 0]]}]
    }
    
    # 예측 요청
    headers = {"content-type": "application/json"}
    response = requests.post(server_url, data=json.dumps(data), headers=headers)
    
    if response.status_code == 200:
        predictions = response.json().get('predictions')
        print("REST API 결과:", predictions)
    else:
        print(f"예측 요청 실패. 상태 코드: {response.status_code}, 응답: {response.text}")

rest_test()