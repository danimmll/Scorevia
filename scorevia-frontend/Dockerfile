FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
# La app SSR resuelve /browser como ruta relativa a su propia carpeta /server (ver
# src/server.ts: join(import.meta.dirname, '../browser')) -- hay que copiar
# dist/scorevia-frontend entero y mantener esa estructura, no aplanarla.
COPY --from=build /app/dist/scorevia-frontend ./dist/scorevia-frontend

EXPOSE 4000
CMD ["node", "dist/scorevia-frontend/server/server.mjs"]
