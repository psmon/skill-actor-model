# 스킬활용 프롬프트 테스트

작성코드는 커밋되지 않으며 스킬을 활용하고 해당 스킬이 코드생성및 수행을 잘하는지 셀프 개선 활동을 위한 프롬프트입니다.

```

## 1. Hello World 액터 코드 

DOC : https://getakka.net/articles/actors/receive-actor-api.html

### Kottlin Pekko Typed
```
/kotlin-pekko-typed skill-test/projects/sample1 하위폴터에 hello world 액터코드 작성, 콘솔프로젝트로 수행
```

### Java Akka Classic
```
/java-akka-classic skill-test/projects/sample2 하위폴터에 hello world 액터코드 작성, 콘솔프로젝트로 수행
```

### C# Akka.NET
```
/dotnet-akka-net skill-test/projects/sample3 하위폴터에 hello world 액터코드 작성, 콘솔프로젝트로 수행
```

## 2. Router

DOC : https://getakka.net/articles/actors/routers.html

### Kottlin Pekko Typed
```
/kotlin-pekko-typed hello world액터모델을 PoolRouter이용해 5개의 분배작업자를 구성 RoundRobin,Broadcast,ConsistentHashing,Random 콘솔프로젝트로 수행 \
skill-test/projects/sample4 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함)
```

### Java Akka Classic
```
/java-akka-classic hello world액터모델을 PoolRouter이용해 5개의 분배작업자를 구성 RoundRobin,Broadcast,ConsistentHashing,Random 콘솔프로젝트로 수행 \
skill-test/projects/sample5 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함)
```

### C# Akka.NET
```
/dotnet-akka-net hello world액터모델을 PoolRouter이용해 5개의 분배작업자를 구성 RoundRobin,Broadcast,ConsistentHashing,Random 콘솔프로젝트로 수행 \
skill-test/projects/sample6 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함)
```

## 3. Throttle

DOC : https://doc.akka.io/libraries/akka-core/current/stream/operators/Source-or-Flow/throttle.html

### Kottlin Pekko Typed
```
/kotlin-pekko-typed 이벤트 발생시 Throttle장치를 이용해 초당 3으로 처리제어를 하는 액터모델을 작성, 오버플로우 전략으로 100이넘으면 드롭할것  \
skill-test/projects/sample7 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### Java Akka Classic
```
/java-akka-classic 이벤트 발생시 Throttle장치를 이용해 초당 3으로 처리제어를 하는 액터모델을 작성, 오버플로우 전략으로 100이넘으면 드롭할것  \
skill-test/projects/sample8 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### C# Akka.NET
```
/dotnet-akka-net 이벤트 발생시 Throttle장치를 이용해 초당 3으로 처리제어를 하는 액터모델을 작성, 오버플로우 전략으로 100이넘으면 드롭할것  \
skill-test/projects/sample9 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

-- Codex 실험
- skill-maker 클코드 스킬을 Codex 등록요청
- Codex의 스킬은 슬래쉬를빼고 요청가능

## 4. Persistence

DOC : https://getakka.net/articles/persistence/architecture.html

### Kottlin Pekko Typed
```
/kotlin-pekko-typed 액터는 인메모리 처리되어 알수없는 종료후 재구동시 복원처리가 안됩니다. 퍼시던트 sqllite를 이용해 액터이벤트가 영속성될수 있도록 작성  \
skill-test/projects/sample10 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### Java Akka Classic
```
/java-akka-classic 액터는 인메모리 처리되어 알수없는 종료후 재구동시 복원처리가 안됩니다. 퍼시던트 sqllite를 이용해 액터이벤트가 영속성될수 있도록 작성  \
skill-test/projects/sample11 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### C# Akka.NET
```
/dotnet-akka-net 액터는 인메모리 처리되어 알수없는 종료후 재구동시 복원처리가 안됩니다. 퍼시던트 sqllite를 이용해 액터이벤트가 영속성될수 있도록 작성  \
skill-test/projects/sample12 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

## 4. workingwithgraphs

DOC : https://getakka.net/articles/streams/workingwithgraphs.html

### Kottlin Pekko Typed
```
/kotlin-pekko-typed AkkaStream의 WorkingWithGraph컨셉 구현,소스(랜덤 1~100)-> 분기 (fan1: src+2,fan2: src+10) -> 머지(fan1+fan2) -> Out(printf)  \
skill-test/projects/sample13 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### Java Akka Classic
```
/java-akka-classic AkkaStream의 WorkingWithGraph컨셉 구현,소스(랜덤 1~100)-> 분기 (fan1: src+2,fan2: src+10) -> 머지(fan1+fan2) -> Out(printf)  \
skill-test/projects/sample14 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### C# Akka.NET
```
/dotnet-akka-net AkkaStream의 WorkingWithGraph컨셉 구현,소스(랜덤 1~100)-> 분기 (fan1: src+2,fan2: src+10) -> 머지(fan1+fan2) -> Out(printf)  \
skill-test/projects/sample15 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### 5. FSM Actor with Timer

DOC : 다음 컨셉을 Kotlin Pekko Typed, Java Akka Classic, C# Akka.NET으로 구현, 각언어맞는 스킬을 참고할것
- https://getakka.net/articles/actors/schedulers.html
- https://getakka.net/articles/actors/finite-state-machine.html
- 스킬경로 : plugins

### Kottlin Pekko Typed
```
/kotlin-pekko-typed FSMActor를 이용 실시간 정크처리 인서트.. event1,2,3,4,5 가 왔을때 매번 인서트가아닌 3초마다 모은만큼 최대 100개씩 저장..저장장치는 SQLlite장치이용할것   \
skill-test/projects/sample16 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### Java Akka Classic
```
/java-akka-classic FSMActor를 이용 실시간 정크처리 인서트.. event1,2,3,4,5 가 왔을때 매번 인서트가아닌 3초마다 모은만큼 최대 100개씩 저장..저장장치는 SQLlite장치이용할것  \
skill-test/projects/sample17 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```

### C# Akka.NET
```
/dotnet-akka-net FSMActor를 이용 실시간 정크처리 인서트.. event1,2,3,4,5 가 왔을때 매번 인서트가아닌 3초마다 모은만큼 최대 100개씩 저장..저장장치는 SQLlite장치이용할것  \
skill-test/projects/sample18 하위폴터에 프로젝트 생성, 생성후 실행해 콘솔결과 알려줄것(콘솔로그는 작성된 기능을 잘설명하는 로깅이여야함) \
스킬을통한 코드생성이라 skill-test/projects 하위 이미작성된 샘플코드는 참고하지말것
```




## 테스트 문서 
- skill-test/projects 하위 프로젝트들을 수행해 작동결과를 skill-test/TEST-RESULT.md 에 기록및 업데이트
- 주로 콘솔로 작성되었으며 콘솔모드로 수행한 콘솔로그를 파악
- 유닛테스트가 있는경우 유닛테스트도 수행
- 테스트 결과리포팅에 간단하게 프로젝트 컨셉도 설명할것
- 프로젝트별 1회 테스트이기때문에 테스트가 기록된 프로젝트는 건너뛰어도되며 새롭게 추가된 프로젝트만 수행할것

## 스킬업데이트 지침

### 스킬업데이트 전체지침
- skill-test/projects 생성된 하위 프로젝트 코드를 참고해 스킬에 더욱 다양한 액터모델 패턴을 반영해 스킬을 개선합니다.
  - sample16 ~ sample19 생성된코드로 한정참조합니다. (나머지는 반영완료됨) 
  - skill-maker/Skill-MarketPlace.md : 스킬 플러그인을 관리하는 방법을 먼저참고합니다.
  - plugins : 스킬구성된 플로그인 경로입니다.
  - 기존 스킬을 참고해 개선합니다. 중복인경우 업데이트를 하지 않습니다.
  - .claude-plugin/marketplace.json 버전도 업데이트해줄것

### 스킬업데이트 부분지침 - 클코드 컨텍스트내 코드오류및 개선사항이 진행되었을때 부분수행
- plugins/skill-actor-model/skills/kotlin-pekko-typed 에 방금 개선한부분을 참고 실수하지않도록 스킬업데이트, 추가된컨셉이 있다고하면 추가업데이트
- plugins/skill-actor-model/skills/java-akka-classic 에 방금 개선한부분을 참고 실수하지않도록 스킬업데이트, 추가된컨셉이 있다고하면 추가업데이트
- plugins/skill-actor-model/skills/dotnet-akka-net 에 방금 개선한부분을 참고 실수하지않도록 스킬업데이트, 추가된컨셉이 있다고하면 추가업데이트