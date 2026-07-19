# M7 프론트 v2 (React) — 구 web/Dockerfile 과 동일 2-stage 구조, 별도 이미지로 공존 (spec-02 T5: 롤백 = 이미지 전환)
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# prod 빌드: mock off(기본) · API base = same-origin(nginx 프록시 전제)
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80 443
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
