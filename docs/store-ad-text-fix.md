# 스토어 설명에서 "광고 없음" 단락 바꾸기 (12개 언어)

광고가 들어가는 업데이트(versionCode 7)를 올리기 전에, 플레이 콘솔의 **자세한 설명**에서
아래 단락을 바꾼다. 2026-10-03 에 실제 스토어 페이지를 언어별로 열어 확인했을 때
12개 언어 전부에 "광고 없음 / 인터넷 권한조차 없음" 단락이 들어 있었다.

**성장 → 스토어 등록정보 → 기본 스토어 등록정보** → 위쪽 언어 드롭다운에서 언어를 바꿔 가며
각 언어의 자세한 설명에서 **■ 로 시작하는 그 단락만** 지우고 오른쪽 두 줄을 붙여 넣는다.
나머지 문장은 그대로 둔다. 광고가 있다고 새로 적지는 않는다 — 스토어 페이지에
"광고 포함" 표시가 자동으로 붙는다.

| 언어 | 지울 단락 (첫 줄) | 붙여 넣을 두 줄 |
|---|---|---|
| 한국어 | ■ 광고 없음, 수집 없음 | ■ 카메라 영상은 기기 안에서만<br>카메라는 손 모양을 알아보는 데만 쓰이고, 영상은 저장하거나 보내지 않습니다. |
| English | ■ No ads, no data | ■ Your camera stays on your device<br>The camera is used only to recognise your hand; frames are never stored or sent. |
| 日本語 | ■ 広告なし、収集なし | ■ カメラ映像は端末の中だけ<br>カメラは手の形を認識するためだけに使い、映像は保存も送信もしません。 |
| 中文(简体) | ■ 无广告，无收集 | ■ 相机画面只留在设备上<br>相机仅用于识别手型，画面不保存也不发送。 |
| Español | ■ Sin anuncios, sin recopilación | ■ La cámara no sale de tu dispositivo<br>La cámara solo sirve para reconocer tu mano; las imágenes no se guardan ni se envían. |
| Português | ■ Sem anúncios, sem coleta | ■ A câmera fica no seu aparelho<br>A câmera serve apenas para reconhecer sua mão; as imagens não são salvas nem enviadas. |
| Deutsch | ■ Keine Werbung, keine Daten | ■ Kamerabilder bleiben auf dem Gerät<br>Die Kamera dient nur der Handerkennung; Bilder werden weder gespeichert noch gesendet. |
| Français | ■ Sans publicité, sans collecte | ■ La caméra reste sur ton appareil<br>La caméra sert uniquement à reconnaître ta main ; les images ne sont ni conservées ni envoyées. |
| Русский | ■ Без рекламы, без сбора данных | ■ Видео с камеры остаётся на устройстве<br>Камера нужна только для распознавания руки; кадры не сохраняются и не отправляются. |
| Indonesia | ■ Tanpa iklan, tanpa pengumpulan data | ■ Kamera hanya di perangkat Anda<br>Kamera hanya dipakai untuk mengenali tangan; gambarnya tidak disimpan atau dikirim. |
| Tiếng Việt | ■ Không quảng cáo, không thu thập | ■ Camera chỉ xử lý trên thiết bị<br>Camera chỉ dùng để nhận diện bàn tay; hình ảnh không được lưu hay gửi đi. |
| ไทย | ■ ไม่มีโฆษณา ไม่เก็บข้อมูล | ■ ภาพจากกล้องอยู่แค่ในเครื่อง<br>กล้องใช้เพื่อจดจำรูปมือเท่านั้น ภาพจะไม่ถูกบันทึกหรือส่งออกไป |

전체 설명은 `PLAY_CONSOLE.md` 5절에 이미 바뀐 상태로 들어 있다.
