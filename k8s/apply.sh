# Apply order for a local cluster (images must already exist as bookstore/*:1.0.0).
kubectl apply -f namespace.yaml
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml
kubectl apply -f infra/
kubectl apply -f config-server-deployment.yaml -f config-server-service.yaml
kubectl apply -f user-service-deployment.yaml -f user-service-service.yaml
kubectl apply -f book-service-deployment.yaml -f book-service-service.yaml
kubectl apply -f order-service-deployment.yaml -f order-service-service.yaml
kubectl apply -f payment-service-deployment.yaml -f payment-service-service.yaml
kubectl apply -f notification-service-deployment.yaml -f notification-service-service.yaml
kubectl apply -f analytics-service-deployment.yaml -f analytics-service-service.yaml
kubectl apply -f gateway-deployment.yaml -f gateway-service.yaml -f gateway-hpa.yaml
