# BiteAndByte-Restaurant
BiteAndByte Restaurant is an online food ordering platform: public menu, customer cart and orders, Razorpay + Cash on Delivery payments, email notifications, and an admin dashboard for menu, stock, orders and revenue

backend The wrapper downloads Maven

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"     # needed once per shell; JAVA_HOME is not set globally
.\mvnw.cmd spring-boot:run                            # dev profile (H2 in-memory) by default
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=oracle"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
.\mvnw.cmd test
.\mvnw.cmd clean package


Frontend (run from `frontend/`):

in powershell

npm install
npm run dev       # Vite on :5173, proxies /api and /actuator to :8080
npm test          # Vitest, single run
npm run lint      # oxlint
npm run build
