(in-ns 'supremereality.core)

(defn wrap-exception-handling
          [handler]
          (fn [request]
            (try
              (handler request)
              (catch Exception e
                {:status 500 :body (selmer/render-file "setuperr.html" {})}))))

(defn wrap-spamcheck
          [handler]
          (fn [request]
            (if (contains? (:params request) "password_b") 
                (if 
                  (= (get (:params request) "password_b") "") (handler request) 
                    {:status 200 :body (selmer/render-file "spam.html" {})}) 
              (handler request))
             ))

(defn wrap-floodcheck
  [handler]
  (fn [request]
    (if 
     (and 
      (= :post (:request-method request)) 
      (not (contains? (:session request) :mod)) 
      (not (contains? (:session request) :owner)) 
      (not (contains? (:session request) :admin)) 
      (not (contains? (:session request) :setup))) 
      (detect-flooduser handler request) (handler request))))