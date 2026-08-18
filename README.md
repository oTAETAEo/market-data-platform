# Market Data Platform — Architecture Overview

## 아키텍처

<img width="1276" height="896" alt="Image" src="https://github.com/user-attachments/assets/6daa7060-8161-40a9-b795-7a88b5e9c48e" />

## 계층별 역할

| 계층 | 역할 |
| --- | --- |
| Collector Services | 공급자별 WebSocket 연결, 인증, 스트림 구독, DTO 파싱 및 재연결 처리 |
| core-domain | 공급자 형식에 독립적인 체결·호가·캔들·DLQ 이벤트 계약 제공 |
| Kafka Cluster | 이벤트 종류별 토픽을 통해 producer와 consumer를 비동기로 분리 |
| Consumer Services | 최신 시세·시계열 저장 및 클라이언트 대상 실시간 피드 전송 |
