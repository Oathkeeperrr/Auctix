# ---------- build ----------
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
# `npm ci` when a lockfile is present, otherwise a plain install.
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

COPY . .
RUN npm run build

# ---------- runtime ----------
FROM nginx:1.27-alpine AS runtime

# SPA routing: unknown paths fall back to index.html.
COPY docker/nginx-spa.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=4s --start-period=10s --retries=5 \
  CMD ["sh", "-c", "wget -q --spider http://127.0.0.1/ || exit 1"]

CMD ["nginx", "-g", "daemon off;"]
