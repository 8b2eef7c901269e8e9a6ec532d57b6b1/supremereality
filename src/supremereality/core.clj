(ns supremereality.core
  (:gen-class) 
  (:use [org.httpkit.server :only [run-server]])
  (:require [ring.util.http-response :as response]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.anti-forgery :refer :all]
            [ring.middleware.session :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [selmer.parser :as selmer]
            [ring.util.anti-forgery :as anti-forgery]
            [clojure.java.jdbc :as jdbc]
            [buddy.hashers :as hashers]
            [java-time :as time]
            [overtone.at-at :as sched]
            [clojure.string :as str])
  (:import [java.awt.image AffineTransformOp BufferedImage]
           [java.io ByteArrayOutputStream FileInputStream]
           java.awt.geom.AffineTransform
           javax.imageio.ImageIO
           java.net.URLEncoder
           java.io.ByteArrayInputStream))

;;UUID seed (gives your users a unique id, prefer a prime number)
(def uuid-seed 15485857)

;;stateful memory keeps track of flood IP address
(def floodlist (atom {}))

;;time in between posts (in seconds)
(def time2flood 20)

(load "utils")
(load "database")
(load "handlers")
(load "routes")
(load "middleware")

;;scheduled tasks
(def my-pool (sched/mk-pool))

;;main program
(defn -main []
  (do (sched/interspaced 900000 #(reset-floodlist) my-pool) ;;time in ms, 900000 = every 15 mins - resets flood list
      (run-server
       (-> #'handler
           wrap-spamcheck
           wrap-floodcheck
           wrap-anti-forgery
           wrap-session
           wrap-params
           wrap-multipart-params
           wrap-restful-format
           wrap-exception-handling)
       {:port 3000
        :join? false
        :max-body 20000000})))

  ;;max-body = max POST upload size including files. 20mb default          