# 플레이 콘솔 등록 절차

위에서부터 순서대로 따라 하면 된다. 콘솔 메뉴 이름은 **굵게**, 입력할 값은 표에 있다.
전세계 대상이므로 12개 언어 문구를 아래 [스토어 문구](#5-스토어-문구-12개-언어) 절에 다 넣어 뒀다.

---

## 0. 시작 전에

- 개발자 계정 (최초 1회 $25) — https://play.google.com/console
- 서명된 AAB — `docs/RELEASE.md` 1~2번 먼저
- 출시용 이미지 (아이콘 512×512, 그래픽 1024×500, 스크린샷 2장 이상)
- 공개 문서 주소 (앱 저장소가 비공개여도 항상 열려 있다):

| 쓸 곳 | 주소 |
|---|---|
| 개인정보처리방침 (필수) | `https://yongminlee2.github.io/legal/waxball/privacy.html` |
| 웹사이트 (선택) | `https://yongminlee2.github.io/legal/waxball/` |
| 지원 안내 | `https://yongminlee2.github.io/legal/waxball/support.html` |

  문서는 별도 공개 저장소 `Yongminlee2/legal` 에 있다. 앱 소스 저장소는 비공개로
  돌려도 이 주소들은 계속 살아 있어야 한다 — 스토어에 등록하는 링크이기 때문이다.

---

## 1. 앱 만들기

콘솔 첫 화면 → **앱 만들기** 버튼.

| 항목 | 입력할 값 |
|---|---|
| 앱 이름 | `왁뿌볼 ASMR` |
| 기본 언어 | 한국어 – ko-KR |
| 앱 또는 게임 | **앱** |
| 유료 또는 무료 | **무료** |

아래 선언 세 개 전부 체크 → **앱 만들기**.

> 무료로 만들면 나중에 유료로 바꿀 수 없다. 인앱결제는 무료 앱에서도 되므로
> 나중에 1.5달러 전체 해금을 붙일 계획이라도 **무료**가 맞다.

---

## 2. 앱 콘텐츠 (왼쪽 메뉴 **정책 → 앱 콘텐츠**)

여기를 다 채워야 출시 버튼이 열린다. 항목마다 **시작** 버튼이 있다.

### 2-1. 개인정보처리방침
```
https://yongminlee2.github.io/legal/waxball/privacy.html
```

### 2-2. 앱 액세스 권한
→ **모든 기능을 특별한 액세스 없이 사용할 수 있음** 선택.
(로그인이 없다.)

### 2-3. 광고
→ **아니요, 앱에 광고가 없습니다.**

> 나중에 광고 해금을 붙이면 이 답을 **예**로 바꾸고 개인정보처리방침도 고쳐야 한다.

### 2-4. 콘텐츠 등급
**설문 시작** → 이메일 입력 → 카테고리 **유틸리티, 생산성, 커뮤니케이션, 기타** 선택.
이후 질문은 전부 **아니요**:

| 질문 | 답 |
|---|---|
| 폭력적인 콘텐츠 | 아니요 |
| 성적인 콘텐츠 | 아니요 |
| 욕설 | 아니요 |
| 통제 물질(술·담배·마약) | 아니요 |
| 도박 | 아니요 |
| 사용자 간 상호작용·위치 공유 | 아니요 |
| 개인정보 수집 | 아니요 |

→ 전체이용가 등급이 나온다.

### 2-5. 타겟층 및 콘텐츠
연령대 선택. **13세 이상**을 권한다.

> 12세 이하를 포함하면 "가족용 앱" 정책이 추가로 적용되어 심사 항목이 늘어난다.
> 이 앱은 광고도 데이터 수집도 없어서 통과는 가능하지만, 첫 출시는 단순한 쪽이 낫다.

"어린이의 관심을 끌 수 있나요" → **아니요** (캐릭터 볼이 있지만 주 대상은 성인 ASMR 이용자).

### 2-6. 데이터 보안 ★ 여기가 제일 헷갈린다

| 질문 | 답 |
|---|---|
| 앱에서 필수 사용자 데이터 유형을 수집하거나 공유하나요? | **아니요** |

→ **아니요**를 고르면 나머지 질문이 전부 사라진다.

> **카메라를 쓰는데 왜 "아니요"인가:** 구글 기준으로 "수집"은 데이터가 **기기 밖으로
> 나가는 것**을 말한다. 이 앱은 카메라 영상을 기기 안에서만 처리하고 즉시 버리며
> 저장도 전송도 하지 않는다. 인터넷 권한 자체가 없다. 그래서 수집이 아니다.

### 2-7. 나머지 (전부 아니요/해당 없음)
정부 앱 · 금융 기능 · 건강 앱 · 뉴스 앱 → 전부 **아니요**.

---

## 3. 스토어 등록정보 (왼쪽 메뉴 **성장 → 스토어 설정 → 기본 스토어 등록정보**)

먼저 **한국어**로 채운다. 값은 아래 [스토어 문구](#5-스토어-문구-12개-언어) 표에서 복사.

| 칸 | 제한 | 넣을 것 |
|---|---|---|
| 앱 이름 | 30자 | 표의 "앱 이름" |
| 간단한 설명 | 80자 | 표의 "간단한 설명" |
| 자세한 설명 | 4000자 | 표의 "자세한 설명" |

**그래픽 항목:**

| 항목 | 규격 | 파일 |
|---|---|---|
| 앱 아이콘 | 512×512 PNG | `docs/icons/_스토어_512.png` |
| 그래픽 이미지 | 1024×500 | 준비한 것 |
| 휴대전화 스크린샷 | 최소 2장, 최대 8장 | 준비한 것 |

**연락처 정보** (같은 화면 아래쪽):

| 칸 | 값 |
|---|---|
| 이메일 (필수) | `dydals5678@gmail.com` |
| 웹사이트 | `https://yongminlee2.github.io/legal/waxball/` |
| 전화번호 | 비워 둔다 |

---

## 3-2. 앱 카테고리 (**성장 → 스토어 설정 → 앱 카테고리**)

등록정보와 다른 화면에 있다. 여기서 세 가지를 정한다.

| 항목 | 값 |
|---|---|
| 앱 또는 게임 | 앱 |
| 카테고리 | **엔터테인먼트** |
| 태그 | ASMR·이완·스트레스 해소 같은 것 중 목록에 있는 걸로 최대 5개 |

카테고리는 출시 뒤에도 언제든 바꿀 수 있고, 바꿔도 심사를 다시 받지 않는다.

> 라이프스타일이나 건강/피트니스도 후보지만, ASMR 앱 이용자가 실제로 찾아보는
> 곳은 엔터테인먼트다. 게임 카테고리는 점수·목표가 없어 평가가 박할 수 있다.

---

## 4. 언어 추가 (**성장 → 스토어 설정 → 스토어 등록정보 번역 관리**)

**번역 관리 → 자체 번역 추가** → 아래 11개 언어를 고른다:

```
English (United States) – en-US
日本語 – ja-JP
中文(简体) – zh-CN
Español (España) – es-ES
Português (Brasil) – pt-BR
Deutsch – de-DE
Français – fr-FR
Русский – ru-RU
Bahasa Indonesia – id
Tiếng Việt – vi
ภาษาไทย – th
```

추가하면 언어마다 등록정보 편집 화면이 생긴다. 각 언어에서 **앱 이름·간단한 설명·
자세한 설명** 세 칸을 아래 표대로 채운다. 스크린샷은 언어별로 안 넣어도 되고,
안 넣으면 기본 언어(한국어) 것이 쓰인다.

> 앱 **안**의 글자(버튼·안내문)는 이미 12개 언어가 앱에 들어 있어서 따로 할 것이 없다.
> 여기서 넣는 것은 **스토어 페이지에 보이는 글**이다. 둘은 별개다.

---

## 5. 스토어 문구 (12개 언어)

앱 이름은 브랜드라서 한국어·일본어·중국어만 현지 문자로 쓰고 나머지는 로마자 그대로 둔다.

### 한국어 (ko-KR) — 기본

**앱 이름**
```
왁뿌볼 ASMR
```
**간단한 설명**
```
손바닥에 올려놓고 쥐어서 부수는 왁스볼 촉감 ASMR
```
**자세한 설명**
```
카메라에 손바닥을 펴서 보여주면 그 위에 왁스볼이 올라옵니다.
손을 쥐면 쥔 만큼 부서집니다. 실제 왁뿌볼을 쥐는 동작 그대로입니다.

■ 진짜 소리
직접 녹음한 왁뿌볼 소리를 씁니다. 합성음이 아니라 실제로 부술 때 나는
"빠자자작" 소리를 재질별로 나눠 담았습니다. 이어폰을 끼면 더 좋습니다.

■ 볼 36종
지구·목성·토성 같은 실제 행성부터 농구공·주사위·판다·고양이,
그리고 파스텔 색이 섞인 반죽볼까지. 볼마다 소리가 다릅니다.

■ 부순 뒤가 진짜
껍질이 다 깨져도 끝이 아닙니다. 고무 껍질 안에 조각이 남아 있고,
계속 주무르면 조각이 속과 섞이면서 색이 천천히 바뀝니다.

■ 광고 없음, 수집 없음
광고가 없습니다. 개인정보를 수집하지 않습니다.
인터넷 권한조차 없어서 어떤 데이터도 기기 밖으로 나가지 않습니다.
카메라는 손 모양을 알아보는 데만 쓰이고, 영상은 저장하거나 보내지 않습니다.

■ 12개 언어
한국어·영어·일본어·중국어·스페인어·포르투갈어·독일어·프랑스어·
러시아어·인도네시아어·베트남어·태국어

카메라 권한이 필요합니다. 허락하지 않아도 앱은 켜집니다.
```

### English (en-US)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Rest a wax ball on your palm and squeeze. Real recorded crushing sounds.
```
**자세한 설명**
```
Show your open palm to the camera and a wax ball settles onto it.
Squeeze your hand and it crumbles, just as hard as you squeeze.

■ Real sounds
Every crunch is a real recording, not synthesis. The crackle of an actual
wax ball being crushed, sorted by material. Best with headphones.

■ 36 balls
Real planets like Earth, Jupiter and Saturn, plus a basketball, a die,
a panda, a cat, and pastel marbled dough balls. Each one sounds different.

■ The best part comes after
Breaking the shell isn't the end. The pieces stay trapped inside a rubber
skin, and if you keep kneading they blend into the core and the colour
slowly shifts.

■ No ads, no data
No advertising. No data collection. The app does not even hold the internet
permission, so nothing can leave your device. The camera is used only to
recognise your hand; frames are never stored or sent.

■ 12 languages
Korean, English, Japanese, Chinese, Spanish, Portuguese, German, French,
Russian, Indonesian, Vietnamese, Thai.

Camera permission is required. The app still opens if you decline.
```

### 日本語 (ja-JP)

**앱 이름**
```
ワックスボール ASMR
```
**간단한 설명**
```
手のひらに乗せて握って砕く、ワックスボールの触感ASMR
```
**자세한 설명**
```
カメラに手のひらを開いて見せると、その上にワックスボールが乗ります。
手を握ると握った分だけ砕けます。本物を握る動作そのままです。

■ 本物の音
実際に録音したワックスボールの音を使っています。合成音ではなく、
本当に砕くときの「パチパチ」という音を素材ごとに収めました。
イヤホンで聴くとより楽しめます。

■ ボール36種
地球・木星・土星などの実在の惑星から、バスケットボール・サイコロ・
パンダ・ねこ、パステル色が混ざった粘土ボールまで。音はそれぞれ違います。

■ 砕いたあとが本番
殻が全部割れても終わりではありません。ゴムの膜の中に破片が残り、
こね続けると中身と混ざって色がゆっくり変わっていきます。

■ 広告なし、収集なし
広告はありません。個人情報を収集しません。
インターネット権限すらないため、データが端末の外に出ることはありません。
カメラは手の形を認識するためだけに使い、映像は保存も送信もしません。

■ 12言語対応
韓国語・英語・日本語・中国語・スペイン語・ポルトガル語・ドイツ語・
フランス語・ロシア語・インドネシア語・ベトナム語・タイ語

カメラ権限が必要です。許可しなくてもアプリは起動します。
```

### 中文(简体) (zh-CN)

**앱 이름**
```
捏蜡球 ASMR
```
**간단한 설명**
```
放在手掌上握紧捏碎，蜡球触感ASMR
```
**자세한 설명**
```
把手掌摊开对准相机，蜡球就会落在你的手上。
握紧手，蜡球会随着你用力的程度一点点碎开。和真的捏蜡球一模一样。

■ 真实的声音
使用亲手录制的蜡球声音，不是合成音。真正捏碎时那种"噼里啪啦"的
声音，按材质分类收录。戴上耳机效果更好。

■ 36种球
从地球、木星、土星等真实行星，到篮球、骰子、熊猫、小猫，
以及混着粉彩色的黏土球。每一种的声音都不一样。

■ 捏碎之后才是重点
外壳全碎了并不是结束。碎片被留在橡胶膜里，
继续揉捏，碎片会和里面混在一起，颜色慢慢改变。

■ 无广告，无收集
没有广告。不收集任何个人信息。
应用甚至没有网络权限，任何数据都不会离开你的设备。
相机仅用于识别手型，画面不保存也不发送。

■ 支持12种语言
韩语、英语、日语、中文、西班牙语、葡萄牙语、德语、法语、
俄语、印尼语、越南语、泰语

需要相机权限。即使拒绝，应用也能打开。
```

### Español (es-ES)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Pon la bola de cera en tu palma y aprieta. Sonidos reales grabados.
```
**자세한 설명**
```
Muestra tu palma abierta a la cámara y una bola de cera se posa sobre ella.
Aprieta la mano y se desmorona, tanto como aprietes.

■ Sonidos reales
Cada crujido es una grabación real, no síntesis. El chasquido de una bola
de cera al romperse, clasificado por material. Mejor con auriculares.

■ 36 bolas
Planetas reales como la Tierra, Júpiter y Saturno, además de un balón de
baloncesto, un dado, un panda, un gato y bolas de masa con vetas pastel.
Cada una suena distinta.

■ Lo mejor viene después
Romper la cáscara no es el final. Los trozos quedan atrapados dentro de una
piel de goma y, si sigues amasando, se mezclan con el interior y el color
cambia poco a poco.

■ Sin anuncios, sin recopilación
Sin publicidad. No recopilamos datos personales. La aplicación ni siquiera
tiene permiso de internet, así que nada sale de tu dispositivo. La cámara
solo sirve para reconocer tu mano; las imágenes no se guardan ni se envían.

■ 12 idiomas
Coreano, inglés, japonés, chino, español, portugués, alemán, francés,
ruso, indonesio, vietnamita y tailandés.

Se necesita permiso de cámara. La aplicación se abre igualmente si lo rechazas.
```

### Português (pt-BR)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Coloque a bola de cera na palma da mão e aperte. Sons reais gravados.
```
**자세한 설명**
```
Mostre a palma da mão aberta para a câmera e uma bola de cera pousa sobre ela.
Aperte a mão e ela se desfaz, na medida em que você aperta.

■ Sons reais
Cada estalo é uma gravação real, não sintetizada. O crepitar de uma bola de
cera sendo esmagada, separado por material. Melhor com fones de ouvido.

■ 36 bolas
Planetas reais como Terra, Júpiter e Saturno, além de bola de basquete,
dado, panda, gato e massinhas com veios em tons pastel.
Cada uma soa diferente.

■ O melhor vem depois
Quebrar a casca não é o fim. Os pedaços ficam presos dentro de uma pele de
borracha e, se você continuar amassando, eles se misturam ao interior e a
cor muda aos poucos.

■ Sem anúncios, sem coleta
Sem publicidade. Não coletamos dados pessoais. O aplicativo nem tem permissão
de internet, então nada sai do seu aparelho. A câmera serve apenas para
reconhecer sua mão; as imagens não são salvas nem enviadas.

■ 12 idiomas
Coreano, inglês, japonês, chinês, espanhol, português, alemão, francês,
russo, indonésio, vietnamita e tailandês.

É necessária permissão de câmera. O aplicativo abre mesmo se você recusar.
```

### Deutsch (de-DE)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Wachsball auf die Handfläche legen und zerdrücken. Echte Aufnahmen.
```
**자세한 설명**
```
Zeig der Kamera deine offene Handfläche und ein Wachsball legt sich darauf.
Ballst du die Hand, zerbröselt er – genau so stark, wie du drückst.

■ Echte Klänge
Jedes Knacken ist eine echte Aufnahme, keine Synthese. Das Knistern eines
zerdrückten Wachsballs, nach Material sortiert. Am besten mit Kopfhörern.

■ 36 Bälle
Echte Planeten wie Erde, Jupiter und Saturn, dazu ein Basketball, ein Würfel,
ein Panda, eine Katze und Knetbälle mit Pastellmarmorierung.
Jeder klingt anders.

■ Das Beste kommt danach
Die Schale zu knacken ist nicht das Ende. Die Stücke bleiben in einer
Gummihaut gefangen, und wenn du weiterknetest, vermischen sie sich mit dem
Kern und die Farbe verändert sich langsam.

■ Keine Werbung, keine Daten
Keine Werbung. Keine Datenerhebung. Die App besitzt nicht einmal die
Internet-Berechtigung, es kann also nichts dein Gerät verlassen. Die Kamera
dient nur der Handerkennung; Bilder werden weder gespeichert noch gesendet.

■ 12 Sprachen
Koreanisch, Englisch, Japanisch, Chinesisch, Spanisch, Portugiesisch,
Deutsch, Französisch, Russisch, Indonesisch, Vietnamesisch, Thailändisch.

Kamerazugriff wird benötigt. Die App startet auch, wenn du ihn ablehnst.
```

### Français (fr-FR)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Pose la boule de cire sur ta paume et serre. Sons réels enregistrés.
```
**자세한 설명**
```
Montre ta paume ouverte à la caméra : une boule de cire vient s'y poser.
Serre la main et elle s'effrite, autant que tu serres.

■ Des sons réels
Chaque craquement est un vrai enregistrement, pas une synthèse. Le
crépitement d'une boule de cire que l'on écrase, trié par matière.
Encore mieux au casque.

■ 36 boules
De vraies planètes comme la Terre, Jupiter et Saturne, mais aussi un ballon
de basket, un dé, un panda, un chat et des boules de pâte marbrée pastel.
Chacune sonne différemment.

■ Le meilleur vient après
Briser la coque n'est pas la fin. Les morceaux restent piégés dans une peau
de caoutchouc et, si tu continues à malaxer, ils se mêlent au cœur et la
couleur change peu à peu.

■ Sans publicité, sans collecte
Aucune publicité. Aucune collecte de données. L'application n'a même pas la
permission internet : rien ne peut quitter ton appareil. La caméra sert
uniquement à reconnaître ta main ; les images ne sont ni conservées ni envoyées.

■ 12 langues
Coréen, anglais, japonais, chinois, espagnol, portugais, allemand, français,
russe, indonésien, vietnamien et thaï.

L'accès à la caméra est requis. L'application s'ouvre même si tu le refuses.
```

### Русский (ru-RU)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Положите восковой шарик на ладонь и сожмите. Настоящие записи звуков.
```
**자세한 설명**
```
Покажите камере раскрытую ладонь — на неё ляжет восковой шарик.
Сожмите руку, и он начнёт крошиться ровно настолько, насколько вы сжали.

■ Настоящие звуки
Каждый хруст — это реальная запись, а не синтез. Треск по-настоящему
раздавленного воскового шарика, разложенный по материалам.
В наушниках звучит лучше.

■ 36 шариков
Настоящие планеты — Земля, Юпитер, Сатурн, а также баскетбольный мяч,
кубик, панда, котик и шарики из пастельной мраморной массы.
Каждый звучит по-своему.

■ Самое интересное — потом
Разбить оболочку — это не конец. Осколки остаются внутри резиновой плёнки,
и если продолжать мять, они смешиваются с сердцевиной, а цвет медленно
меняется.

■ Без рекламы, без сбора данных
Никакой рекламы. Никакого сбора персональных данных. У приложения нет даже
разрешения на доступ в интернет, поэтому ничто не покидает ваше устройство.
Камера нужна только для распознавания руки; кадры не сохраняются и не отправляются.

■ 12 языков
Корейский, английский, японский, китайский, испанский, португальский,
немецкий, французский, русский, индонезийский, вьетнамский, тайский.

Требуется доступ к камере. Приложение откроется и без него.
```

### Bahasa Indonesia (id)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Letakkan bola lilin di telapak tangan dan genggam. Suara rekaman asli.
```
**자세한 설명**
```
Tunjukkan telapak tangan terbuka ke kamera, lalu bola lilin akan hinggap di atasnya.
Genggam tangan Anda dan bola itu hancur, sekuat Anda menggenggam.

■ Suara asli
Setiap bunyi retak adalah rekaman nyata, bukan sintesis. Bunyi bola lilin
yang benar-benar diremukkan, dikelompokkan menurut bahannya.
Lebih nikmat dengan earphone.

■ 36 bola
Planet sungguhan seperti Bumi, Jupiter, dan Saturnus, ditambah bola basket,
dadu, panda, kucing, serta bola adonan bermarmer warna pastel.
Bunyinya berbeda-beda.

■ Bagian terbaik ada setelahnya
Memecahkan cangkang bukan akhirnya. Pecahannya terperangkap di dalam kulit
karet, dan jika terus diremas, pecahan itu menyatu dengan isinya dan
warnanya berubah perlahan.

■ Tanpa iklan, tanpa pengumpulan data
Tidak ada iklan. Tidak mengumpulkan data pribadi. Aplikasi ini bahkan tidak
memiliki izin internet, sehingga tidak ada data yang keluar dari perangkat Anda.
Kamera hanya dipakai untuk mengenali tangan; gambarnya tidak disimpan atau dikirim.

■ 12 bahasa
Korea, Inggris, Jepang, Mandarin, Spanyol, Portugis, Jerman, Prancis,
Rusia, Indonesia, Vietnam, dan Thailand.

Izin kamera diperlukan. Aplikasi tetap terbuka jika Anda menolaknya.
```

### Tiếng Việt (vi)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
Đặt bóng sáp lên lòng bàn tay và bóp. Âm thanh thu thật.
```
**자세한 설명**
```
Xòe lòng bàn tay hướng vào camera, một quả bóng sáp sẽ nằm lên đó.
Nắm tay lại và nó vỡ vụn, mạnh đến đâu tùy bạn bóp.

■ Âm thanh thật
Mỗi tiếng rắc đều là bản thu thật, không phải âm tổng hợp. Tiếng lạo xạo của
quả bóng sáp bị bóp vỡ, được phân loại theo từng chất liệu.
Nghe bằng tai nghe sẽ hay hơn.

■ 36 quả bóng
Từ những hành tinh có thật như Trái Đất, Sao Mộc, Sao Thổ, đến bóng rổ,
xúc xắc, gấu trúc, mèo và những quả đất nặn vân màu pastel.
Mỗi quả một âm thanh khác nhau.

■ Phần hay nhất là sau khi vỡ
Vỡ lớp vỏ chưa phải là hết. Các mảnh vụn còn nằm trong lớp màng cao su,
và nếu bạn tiếp tục nhào bóp, chúng sẽ hòa vào phần ruột và màu sắc
từ từ đổi khác.

■ Không quảng cáo, không thu thập
Không có quảng cáo. Không thu thập dữ liệu cá nhân. Ứng dụng thậm chí không
có quyền truy cập internet, nên không gì rời khỏi thiết bị của bạn. Camera chỉ
dùng để nhận diện bàn tay; hình ảnh không được lưu hay gửi đi.

■ 12 ngôn ngữ
Hàn, Anh, Nhật, Trung, Tây Ban Nha, Bồ Đào Nha, Đức, Pháp,
Nga, Indonesia, Việt và Thái.

Cần quyền camera. Ứng dụng vẫn mở được nếu bạn từ chối.
```

### ภาษาไทย (th)

**앱 이름**
```
WaxBall ASMR
```
**간단한 설명**
```
วางลูกบอลขี้ผึ้งบนฝ่ามือแล้วบีบ เสียงจริงจากการบันทึก
```
**자세한 설명**
```
แบมือให้กล้องเห็น แล้วลูกบอลขี้ผึ้งจะมาวางอยู่บนฝ่ามือของคุณ
กำมือแล้วมันจะแตกร่วน ตามแรงที่คุณบีบ

■ เสียงจริง
ทุกเสียงแตกคือเสียงบันทึกจริง ไม่ใช่เสียงสังเคราะห์ เสียงกรอบแกรบของ
ลูกบอลขี้ผึ้งที่ถูกบีบจริง ๆ แยกตามชนิดของวัสดุ
ฟังผ่านหูฟังจะได้อรรถรสมากขึ้น

■ ลูกบอล 36 แบบ
ตั้งแต่ดาวเคราะห์จริงอย่างโลก ดาวพฤหัสบดี ดาวเสาร์ ไปจนถึงลูกบาสเกตบอล
ลูกเต๋า แพนด้า แมว และลูกแป้งโดว์ลายหินอ่อนสีพาสเทล
แต่ละแบบมีเสียงต่างกัน

■ ช่วงที่ดีที่สุดคือหลังจากบีบแตก
การทำให้เปลือกแตกยังไม่ใช่จุดจบ เศษต่าง ๆ ยังติดอยู่ในเยื่อยาง
และถ้าคุณนวดต่อไป เศษเหล่านั้นจะผสมเข้ากับเนื้อข้างในและสีจะค่อย ๆ เปลี่ยน

■ ไม่มีโฆษณา ไม่เก็บข้อมูล
ไม่มีโฆษณา ไม่เก็บข้อมูลส่วนบุคคล แอปไม่มีแม้แต่สิทธิ์เข้าถึงอินเทอร์เน็ต
จึงไม่มีข้อมูลใดออกจากเครื่องของคุณ กล้องใช้เพื่อจดจำรูปมือเท่านั้น
ภาพจะไม่ถูกบันทึกหรือส่งออกไป

■ รองรับ 12 ภาษา
เกาหลี อังกฤษ ญี่ปุ่น จีน สเปน โปรตุเกส เยอรมัน ฝรั่งเศส
รัสเซีย อินโดนีเซีย เวียดนาม และไทย

ต้องใช้สิทธิ์กล้อง แอปยังเปิดได้แม้คุณจะปฏิเสธ
```

---

## 6. 국가 선택 (**성장 → 스토어 설정 → 국가/지역**)

전세계 대상이면 **모든 국가 선택**. 나중에 언제든 뺄 수 있다.

---

## 7. AAB 업로드

### 먼저 내부 테스트 (**테스트 및 출시 → 테스트 → 내부 테스트**)

테스터 12명을 여기에 넣는다. 심사 없이 바로 설치되어 확인이 빠르다.

1. **테스터** 탭 → 이메일 목록 만들기 → 12명 이메일 추가
2. **새 버전 만들기** → `app-release.aab` 업로드
3. 출시 노트 입력 (아래 참고)
4. **저장 → 버전 검토 → 내부 테스트로 출시 시작**
5. 테스터에게 참여 링크 전달

### 테스트가 끝나면 프로덕션 (**테스트 및 출시 → 프로덕션**)

같은 방식으로 새 버전을 만들고 **프로덕션으로 출시 시작**.
첫 심사는 보통 며칠~1주일 걸린다.

**출시 노트 예시** (한국어 칸):
```
첫 출시입니다.
손바닥에 왁스볼을 올려놓고 쥐어서 부수세요. 볼 36종, 12개 언어를 지원합니다.
```
영어 칸:
```
First release.
Rest a wax ball on your palm and squeeze it. 36 balls, 12 languages.
```

---

## 8. 나중에 (v2에서 할 것)

광고 해금과 1.5달러 전체 해금은 **첫 출시 뒤**에 붙인다.

- 인앱상품은 앱이 한 번 업로드된 뒤라야 콘솔에서 만들 수 있다
- 광고를 넣으면 **2-3 광고 항목을 "예"로** 바꾸고, **2-6 데이터 보안**도 다시
  신고해야 한다 (광고 ID 수집). 개인정보처리방침도 함께 고쳐야 한다
- 앱 코드에는 잠금 구조(`Progress.unlocked`, `isUnlocked`, `BallSpec.price`)가
  이미 남아 있다. `Progress.fresh()` 한 곳만 고치면 3개만 열린 상태가 된다

---

## 확인용 체크리스트

- [ ] 1. 앱 만들기 (이름·언어·앱·무료)
- [ ] 2-1. 개인정보처리방침 URL
- [ ] 2-2. 앱 액세스 권한 (제한 없음)
- [ ] 2-3. 광고 (없음)
- [ ] 2-4. 콘텐츠 등급 설문
- [ ] 2-5. 타겟층 (13세 이상)
- [ ] 2-6. 데이터 보안 (수집 안 함)
- [ ] 2-7. 나머지 선언
- [ ] 3. 한국어 등록정보 + 아이콘·그래픽·스크린샷
- [ ] 3-2. 앱 카테고리 (엔터테인먼트) + 태그
- [ ] 4. 11개 언어 번역 추가
- [ ] 6. 국가 선택
- [ ] 7. 내부 테스트 업로드 → 테스터 12명
- [ ] 7. 프로덕션 출시
