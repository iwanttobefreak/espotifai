FROM node:20-slim

WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev

COPY server.js ./

RUN mkdir -p /app/data /music

ENV PORT=8088
ENV MUSIC_DIR=/music

EXPOSE 8088

CMD ["node", "server.js"]
