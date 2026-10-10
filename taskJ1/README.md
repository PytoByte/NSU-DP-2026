Генерация ключа (windows)
```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out server.key
```

Запуск сервера
```powershell
./gradlew :server:run --args="5555 8 CN=KeyServer server.key"
```

Запуск клиента
```powershell
./gradlew :client:run --args="localhost 5555 alice ./out"
```