# Use a lightweight Node.js image
FROM node:20-alpine

# Set working directory
WORKDIR /app

# Copy package files first (if you add deps later)
COPY package*.json ./

# Install dependencies (noop if none)
RUN npm install --omit=dev || true

# Copy source
COPY server.mjs ./

# Expose port
EXPOSE 3000

# Run the app
CMD ["node", "server.mjs"]