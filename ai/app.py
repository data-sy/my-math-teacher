import json

from flask import Flask, request, jsonify
import requests
from flask_cors import CORS

from predict import predict

app = Flask(__name__)
CORS(app, resources={r"/*": {"origins": "*"}})
# CORS(app, resources={r"/api/v1/*": {"origins": "http://localhost:5173,http://localhost:8080"}})

@app.route('/')
def hello_world():
    return 'Hello world!'

@app.route('/<int:value>')
def path_variable(value):
    return f'The received value is: {value}'

@app.route('/body')
def request_body():
    request_data = request.get_json()
    value01 = request_data.get('value01')
    value02 = request_data.get('value02')
    return f'The received value is: {value01} and {value02}'

@app.route('/aitest')
def aitest():
    input = [[1171, 1], [467, 1], [1703, 1], [1817, 1], [1698, 1], [623, 0], [1182, 0], [1614, 0], [396, 0],
               [1681, 0], [1564, 1], [461, 1], [782, 1], [593, 1], [1582, 1], [774, 0], [1660, 0], [1583, 0], [790, 0],
               [1531, 0]]
    input_data = [input]
    output_data = predict(input_data)
    response_data = {
        "studentTestId": 4,
        "probabilityList": output_data
    }
    return jsonify(response_data), 200

@app.route('/corstest')
def corstest():
    spring_api_url = 'http://127.0.0.1:8080/api/v1/hello'
    response = requests.get(spring_api_url)
    if response.status_code == 200:
        response_text = response.text
        print(f"Response from Spring server: {response_text}")
        return jsonify(response_text), 200
    else:
        print(f"Failed to retrieve data from Spring server. Status code: {response.status_code}")
        return 'Failed to fetch data from Spring', 500

@app.route('/ai/v1/ai/<int:user_test_id>', methods=['POST'])
def ai(user_test_id):
    # 토큰
    jwt_token = request.headers.get('Authorization')
    headers = {
        "Authorization": jwt_token,
        "Content-Type": "application/json"
    }

    # 스프링 서버에서 ai_input 받기
    spring_api_url = 'http://127.0.0.1:8080/api/v1/ai/'+str(user_test_id)
    response_get = requests.get(spring_api_url, headers=headers)

    if response_get.status_code == 200:
        ai_input_response = response_get.json()
        user_test_id = ai_input_response['userTestId']
        input_data = [ai_input_response['answerCodeResponseList']]
        # 진단
        output = predict(input_data)
        response_data = {
            "userTestId": user_test_id,
            "probabilityList": output
        }
    else:
        return 'Failed to fetch data from Spring 1', 500

    # 스프링 서버로 ai_output 보내기
    spring_api_url2 = 'http://127.0.0.1:8080/api/v1/ai'
    response_post = requests.post(spring_api_url2, json=response_data, headers=headers)

    if response_post.status_code == 200:
        return jsonify(response_data), 200
    else:
        return 'Failed to fetch data from Spring 2', 500


# 맥에서는 5000 포트 번호를 이미 사용중, 5001은 Noe4J에서 사용,  8000 사용하자
if __name__ == '__main__':
    app.run(debug=False, host='0.0.0.0', port=8000)
