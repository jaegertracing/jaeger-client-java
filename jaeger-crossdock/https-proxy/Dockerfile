FROM nginx:alpine

ADD build/proxy.conf /etc/nginx/conf.d/proxy.conf
ADD build/proxy.crt /etc/nginx/proxy.crt
ADD build/proxy.key /etc/nginx/proxy.key

EXPOSE 8080 14443
CMD ["sh", "-c", "while ! { wget --spider -S http://jaeger-collector:14269; }; do echo waiting...; sleep 1; done; nginx -g 'daemon off;'"]
