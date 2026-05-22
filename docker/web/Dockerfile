FROM nginx:alpine

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html /usr/share/nginx/html/
COPY styles /usr/share/nginx/html/styles
COPY scripts /usr/share/nginx/html/scripts

EXPOSE 80
