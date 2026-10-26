# ---------- Stage 1: build the Angular app ----------
# ---------- المرحلة 1: بناء تطبيق Angular ----------
FROM node:22-alpine AS build
WORKDIR /app
# Install dependencies first (cached layer), then copy the source.
# نثبّت المكتبات أولًا (طبقة محفوظة)، بعدين ننسخ الكود.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- Stage 2: serve the static files with nginx ----------
# ---------- المرحلة 2: تقديم الملفات الثابتة عبر nginx ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://localhost/ >/dev/null || exit 1
