(in-ns 'supremereality.core)

(defn response-handler [request]
    (response/ok
      (selmer/render-file "home.html" {:sitename (get-site-name) :tnames (topicnames?) :rdata (recentposts?) :sitestats (site-stats?) :sitenews (site-news?)})))
  
  (defn nfhandler [request]
    (response/ok
      (selmer/render-file "404.html" {})))

(defn mighandler [request]
  (if (not (db-schema-migrated?)) 
    (do (jdbc/db-do-commands db-spec [meta-table-ddl topics-table-ddl threads-table-ddl posts-table-ddl reports-table-ddl bans-table-ddl banned-topics-table-ddl]) (init-setup-auth (response/see-other "/setup/2") request))
     (response/see-other "/error")))

(defn topic-handler [request]
    (response/ok
      (selmer/render-file "topics.html" {:topic-sq (topics?) :ucreated (ucreated?)})))

(defn new-topic-handler [request]
  (if (ucreated?) 
    (response/ok
     (selmer/render-file "newtopic.html" {:csrf (anti-forgery/anti-forgery-field)})) 
    (response/see-other "/error")))
;;
(defn new-topic-create-handler [request]
  (let [pars (:multipart-params request)] 
  (if (and (ucreated?) (existingtopic? (get pars "topicname")) (bannedtopic? (get pars "topicname")))
    (do (insert-topic (get pars "topicname") (get pars "topicdesc") (hashers/derive (get pars "topicpassword")) (if (= (get pars "topicnsfw") "true") true false)) (response/see-other "/topics")) 
    (selmer/render-file "topicfail.html" {}))))

(defn setup-handler [request]
  (response/ok
    (selmer/render-file "setup.html" {})))

(defn setup-two-handler [request]
  (if (is-setup-authenticated request) 
    (response/ok
      (selmer/render-file "setup2.html" {:csrf (anti-forgery/anti-forgery-field)}))
      (response/see-other "/error")))

(defn setup-meta-handler [request]
  (if (is-setup-authenticated request) 
  (do
    (site-data-dml
      (get (:form-params request) "sitename") (hashers/derive (get (:form-params request) "sitepwd")) (get (:form-params request) "utopics")) 
        (response/see-other "/setup/3"))
        (response/see-other "/error")))

(defn setup-three-handler [request]
    (if (is-setup-authenticated request) 
      (end-setup-auth (response/ok
        (selmer/render-file "setup3.html" {})) request)
          (response/see-other "/error")))

(defn general-error-handler [request]
    (response/ok
      (selmer/render-file "setuperr.html" {})))

(defn admin-login-handler [request] 
(if (is-admin-authenticated request) (response/see-other "/admin") 
  (response/ok
   (selmer/render-file "admin.html" {:csrf (anti-forgery/anti-forgery-field)}))))


(defn admin-auth-handler [request]
  (if (hashers/check (get-adm-pwd request) (adminpwd?)) (init-admin-auth (response/see-other "/admin") request) (response/see-other "/error")))

(defn admin-panel-handler [request]
  (if (is-admin-authenticated request) 
  (response/ok
    (selmer/render-file "adminpanel.html" {}))
    (response/see-other "/admin/login")))

(defn auth-logout-handler [request]
  (end-session-auth (response/see-other "/")))

(defn terms-handler [request]
  (response/ok 
    (selmer/render-file "terms.html" {})))

(defn topic-id-handler [request] 
(if (> (:tc (first (topic-count? (:id (:params request))))) 0) 
  (let [topic-id (:id (:params request))
        pdata (map get-paginated-thread (map :thid (get-threads-in-order (get-topic-id-from-topic (:id (:params request))) 0)))]
    (response/ok
      (selmer/render-file "topic.html" {:bannedusertime (is-user-banned? (get-client-ip request) (get-topic-id-from-topic topic-id)) :banneduser (nil? (is-user-banned? (get-client-ip request) (get-topic-id-from-topic topic-id))) :page 0 :tpages (range (get-paginated-thread-count (get-topic-id-from-topic topic-id))) :pagedata pdata :topicname topic-id :topicdesc (boardinfo? topic-id) :csrf (anti-forgery/anti-forgery-field)}))) nil))

(defn topic-id-paginated-handler [request] 
(if (> (:tc (first (topic-count? (:id (:params request))))) 0) 
  (let [topic-id (:id (:params request))
        pdata (map get-paginated-thread (map :thid (get-threads-in-order (get-topic-id-from-topic (:id (:params request))) (parse-int (:page (:params request))) )))]
    (response/ok
      (selmer/render-file "topic.html" {:bannedusertime (is-user-banned? (get-client-ip request) (get-topic-id-from-topic topic-id)) :banneduser (nil? (is-user-banned? (get-client-ip request) (get-topic-id-from-topic topic-id))) :page (parse-int (:page (:params request))) :tpages (range (get-paginated-thread-count (get-topic-id-from-topic topic-id))) :pagedata pdata :topicname topic-id :topicdesc (boardinfo? topic-id) :csrf (anti-forgery/anti-forgery-field)}))) nil))

(defn new-thread-handler [request]
  (let [pars (:multipart-params request)
        ip (get-client-ip request)]
      (let [attachment1 (get pars "attachmentone")
            attachment2 (get pars "attachmenttwo")
            attachment3 (get pars "attachmentthree")]
          (let [numattached (num-non-empty-attachments attachment1 attachment2 attachment3)
                attachmentvec (non-empty-attachments attachment1 attachment2 attachment3)]
               (cond
                 (= numattached 0) (do (prune-topic! (get pars "btopicname")) 
                  (insert-new-thread (get pars "threadtitle") (get pars "btopicname") (get pars "threadbody") ip (contains? pars "spoilered") (weight-calc (get pars "threadbody"))) (response/see-other (str "/topic/" (get pars "btopicname") "/")))
                 (= numattached 1) (do (prune-topic! (get pars "btopicname")) 
                  (insert-new-thread (get pars "threadtitle") (get pars "btopicname") (get pars "threadbody") ip (contains? pars "spoilered") (weight-calc (get pars "threadbody")) (first attachmentvec)) (response/see-other (str "/topic/" (get pars "btopicname") "/")))
                 (= numattached 2) (do (prune-topic! (get pars "btopicname")) 
                  (insert-new-thread (get pars "threadtitle") (get pars "btopicname") (get pars "threadbody") ip (contains? pars "spoilered") (weight-calc (get pars "threadbody")) (first attachmentvec) (second attachmentvec)) (response/see-other (str "/topic/" (get pars "btopicname") "/")))
                 (= numattached 3) (do (prune-topic! (get pars "btopicname")) 
                  (insert-new-thread (get pars "threadtitle") (get pars "btopicname") (get pars "threadbody") ip (contains? pars "spoilered") (weight-calc (get pars "threadbody")) (first attachmentvec) (second attachmentvec) (nth attachmentvec 2)) (response/see-other (str "/topic/" (get pars "btopicname") "/")))
                 :else (response/see-other "/error"))))))

;;images

(defn serve-image-handler [request]
  (let [pars (:params request)]
    (let [iid (:id pars) fext (:ext pars) ordn (:ordn pars)]
      (let [res (response/see-other "/coffee.png")]
        (cond
          (= ordn "0")  (let [data (first (image1? (parse-int iid)))] (let [imgd (:dimg data) typed (:dtype data)]
            (if (and (not= typed "pdf") (not= typed "webm") (not= imgd nil)) 
              {:status 200
              :headers {"Content-Type" (str "image/" typed)}
              :body (ByteArrayInputStream. imgd)} res))) 
          (= ordn "1")  (let [data (first (image2? (parse-int iid)))] (let [imgd (:d2img data) typed (:d2type data)]
            (if (and (not= typed "pdf") (not= typed "webm") (not= imgd nil)) 
              {:status 200
              :headers {"Content-Type" (str "image/" typed)}
              :body (ByteArrayInputStream. imgd)} nil))) 
          (= ordn "2")  (let [data (first (image3? (parse-int iid)))] (let [imgd (:d3img data) typed (:d3type data)]
            (if (and (not= typed "pdf") (not= typed "webm") (not= imgd nil)) 
              {:status 200
              :headers {"Content-Type" (str "image/" typed)}
              :body (ByteArrayInputStream. imgd)} nil)))
      )))))
;;thumbnail handler

(defn serve-thumbs-handler [request]
  (let [pars (:params request)]
    (let [iid (:id pars) fext (:ext pars) ordn (:ordn pars)]
      (let [res (response/see-other "/coffee.png")]
        (cond
          (= ordn "0")  (let [data (first (thumbimg? (parse-int iid)))] (let [imgd (:dimg data) typed (:dtype data)]
            (if (and (not= typed "pdf") (not= typed "webm") (not= imgd nil)) 
              {:status 200
              :headers {"Content-Type" (str "image/" typed)}
              :body (ByteArrayInputStream. imgd)} res))) 
          (= ordn "1")  (let [data (first (thumbimg1? (parse-int iid)))] (let [imgd (:d2img data) typed (:d2type data)]
            (if (and (not= typed "pdf") (not= typed "webm") (not= imgd nil)) 
              {:status 200
              :headers {"Content-Type" (str "image/" typed)}
              :body (ByteArrayInputStream. imgd)} nil))) 
          (= ordn "2")  (let [data (first (thumbimg2? (parse-int iid)))] (let [imgd (:d3img data) typed (:d3type data)]
            (if (and (not= typed "pdf") (not= typed "webm") (not= imgd nil)) 
              {:status 200
              :headers {"Content-Type" (str "image/" typed)}
              :body (ByteArrayInputStream. imgd)} nil)))
      )))))

;;thumbhandler 2

(defn serve-thread-handler [request]
(let [pars (:params request)]
  (let [tdata (thread? (:id pars))]
  (if (> (:tc (first (thread-count? (:id pars)))) 0) 
  (response/ok
    (selmer/render-file "thread.html" {:banneduser (nil? (is-user-banned? (get-client-ip request) (get-topic-from-thread (:id pars)))) 
                                       :bannedusertime (is-user-banned? (get-client-ip request) (get-topic-from-thread (:id pars))) 
                                       :videoplayer true :threadlocked (threadlocked? (:id pars)) 
                                       :tdata (filt-thread tdata) :topicid (get-topic-from-thread (:id pars)) 
                                       :topicname (get-topic-name-from-topic (get-topic-from-thread (:id pars))) 
                                       :csrf (anti-forgery/anti-forgery-field) 
                                       :threadname (:thread (first (threadname? (:id pars)))) 
                                       :indexlink (str "/topic/" (:topic (first (get-cata-link? (:id pars)))) "/0") 
                                       :catalink (str "/topic/" (:topic (first (get-cata-link? (:id pars)))) "/catalog") 
                                       :thread-id (:id pars)})) nil))))

;;webm

(defn serve-webm-handler [request]
  (let [pars (:params request)]
    (let [iid (:id pars) fext (:ext pars) ordn (:ordn pars)]
      (cond
        (= ordn "0") (let [data (first (image1? (parse-int iid)))] (let [imgd (:dimg data) typed (:dtype data)]
          (if (and (= typed "webm") (not= imgd nil)) 
            {:status 200
            :headers {"Content-Type" (str "video/" typed)}
            :body (ByteArrayInputStream. imgd)} nil))) 
        (= ordn "1") (let [data (first (image2? (parse-int iid)))] (let [imgd (:d2img data) typed (:d2type data)]
          (if (and (= typed "webm") (not= imgd nil)) 
            {:status 200
            :headers {"Content-Type" (str "video/" typed)}
            :body (ByteArrayInputStream. imgd)} nil))) 
        (= ordn "2") (let [data (first (image3? (parse-int iid)))] (let [imgd (:d3img data) typed (:d3type data)]
          (if (and (= typed "webm") (not= imgd nil)) 
            {:status 200
            :headers {"Content-Type" (str "video/" typed)}
            :body (ByteArrayInputStream. imgd)} nil)))
      ))))

;; pdf handler
(defn serve-pdf-handler [request]
  (let [pars (:params request)]
    (let [iid (:id pars) fext (:ext pars) ordn (:ordn pars)]
      (cond
        (= ordn "0") (let [data (first (image1? (parse-int iid)))] (let [imgd (:dimg data) typed (:dtype data)]
          (if (and (= typed "pdf") (not= imgd nil)) 
            {:status 200
            :headers {"Content-Type" (str "application/" typed)}
            :body (ByteArrayInputStream. imgd)} nil))) 
        (= ordn "1") (let [data (first (image2? (parse-int iid)))] (let [imgd (:d2img data) typed (:d2type data)]
          (if (and (= typed "pdf") (not= imgd nil)) 
            {:status 200
            :headers {"Content-Type" (str "application/" typed)}
            :body (ByteArrayInputStream. imgd)} nil))) 
        (= ordn "2") (let [data (first (image3? (parse-int iid)))] (let [imgd (:d3img data) typed (:d3type data)]
          (if (and (= typed "pdf") (not= imgd nil)) 
            {:status 200
            :headers {"Content-Type" (str "application/" typed)}
            :body (ByteArrayInputStream. imgd)} nil)))
      ))))

;;;reply

(defn reply-thread-handler [request]
  (let [pars (:multipart-params request)
        ip (get-client-ip request)]
      (let [attachment1 (get pars "attachmentone")
            attachment2 (get pars "attachmenttwo")
            attachment3 (get pars "attachmentthree")]
          (let [numattached (num-non-empty-attachments attachment1 attachment2 attachment3)
                attachmentvec (non-empty-attachments attachment1 attachment2 attachment3)
                thid (parse-int (get pars "thid"))
                tbody (str (get pars "threadbody"))
                spoilered (contains? pars "spoilered")
                bumpthread (contains? pars "bumpthread")
                wgt (weight-calc (get pars "threadbody"))]
               (cond 
                (= numattached 0) (do (insert-new-reply thid tbody (if (= bumpthread true) wgt 0) spoilered ip) (response/see-other (str "/thread/" thid)))
                (> numattached 0) (do (insert-new-reply thid tbody (if (= bumpthread true) wgt 0) spoilered ip attachmentvec numattached) (response/see-other (str "/thread/" thid)))
                :else (response/see-other "/error"))))))

;; catalog

(defn topic-catalog-handler [request] 
  (let [topic-id (:id (:params request))
        pdata (map get-catalog-thread (map :thid (get-catalog-threads-in-order (get-topic-id-from-topic (:id (:params request))) )))]
    (response/ok 
      (selmer/render-file "catalog.html" {:bannedusertime (is-user-banned? (get-client-ip request) (get-topic-id-from-topic topic-id)) 
                                          :banneduser (nil? (is-user-banned? (get-client-ip request) (get-topic-id-from-topic topic-id))) 
                                          :pagedata pdata 
                                          :topicname topic-id 
                                          :topicdesc (boardinfo? topic-id) 
                                          :csrf (anti-forgery/anti-forgery-field)}))))

    ;;help
(defn help-handler [request]
  (response/ok
    (selmer/render-file "help.html" {:sitename (get-site-name)})))

(defn admin-site-name-handler [request]
  (if (is-admin-authenticated request)
    (response/ok
     (selmer/render-file "adminsitename.html" {:sitename (get-site-name) :csrf (anti-forgery/anti-forgery-field)}))
    (response/see-other "/admin/login")))

(defn admin-site-change-handler [request]
  (if (is-admin-authenticated request)
    (let [pars (:multipart-params request)] (do (sitename! (get pars "newsitename")) (response/see-other "/admin/sitename")))
    (response/see-other "/admin/login")))

(defn admin-ucreated-handler [request]
  (if (is-admin-authenticated request)
    (response/ok
     (selmer/render-file "adminucreated.html" {:ucreatedtopics (ucreated?) :csrf (anti-forgery/anti-forgery-field)}))
    (response/see-other "/admin/login")))

(defn admin-ucreated-change-handler [request]
  (if (is-admin-authenticated request)
    (do (ucreated!) (response/see-other "/admin/ucreated"))
    (response/see-other "/admin/login")))

(defn admin-news-handler [request]
  (if (is-admin-authenticated request)
    (response/ok
     (selmer/render-file "adminnews.html" {:blurb (site-news-edit?) :csrf (anti-forgery/anti-forgery-field)}))
    (response/see-other "/admin/login")))

(defn admin-news-change-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-admin-authenticated request)
      (do (sitenews! (get pars "newnews")) (response/see-other "/admin/news"))
      (response/see-other "/admin/login"))))

(defn admin-password-handler [request]
  (if (is-admin-authenticated request)
    (response/ok
     (selmer/render-file "adminpassword.html" {:csrf (anti-forgery/anti-forgery-field)}))
    (response/see-other "/admin/login")))

(defn admin-password-change-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-admin-authenticated request)
      (do (adminpassword! (hashers/derive (get pars "newadminpassword"))) (end-session-auth (response/see-other "/admin/login")))
      (response/see-other "/admin/login"))))

(defn report-handler [request]
  (let [pars (:params request)]
    (let [topicid (parse-int (get pars "topicid")) 
          threadid (parse-int (get pars "threadid")) 
          postid (parse-int (get pars "postid"))]
      (do 
        (insert-report! topicid threadid postid)
        (response/ok
         (selmer/render-file "reported.html" {:topicid topicid 
                                              :threadid threadid 
                                              :postid postid}
                             ))))))

(defn report-page-handler [request]
  (let [pars (:params request)]
    (selmer/render-file "reportpage.html" 
                        {:topicid (get-topic-id-from-topic (:topicid pars)) 
                         :topicname (:topicid pars)
                         :threadid (parse-int (:threadid pars)) 
                         :postid (parse-int (:postid pars))
                         :csrf (anti-forgery/anti-forgery-field)}
                        )))

(defn admin-ban-topic-handler [request]
  (if (is-admin-authenticated request)
    (response/ok
     (selmer/render-file "adminbantopic.html" {:csrf (anti-forgery/anti-forgery-field) :btopics (bannedtopics?)}))
    (response/see-other "/admin/login")))

(defn admin-ban-topic-change-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-admin-authenticated request)
      (do (bantopic! (get pars "topicnameb")) (response/see-other "/admin/bantopic"))
      (response/see-other "/admin/login"))))

(defn admin-reports-handler [request]
  (if (is-admin-authenticated request)
    (response/ok
     (selmer/render-file "adminreports.html" {:greports (globalreports?)}))
    (response/see-other "/admin/login")))

(defn delete-handler [request]
  (let [ipaddr (get-client-ip request) pars (:params request)]
    (if (deletepost? (:postid pars) ipaddr)
      (do (deletepost! (:postid pars)) (response/ok
       (selmer/render-file "delete.html" {:postid (:postid pars)})))
      (response/ok
       (selmer/render-file "cantdelete.html" {})))))

(defn mod-panel-handler [request]
  (if (is-mod-authenticated request) (response/see-other "/mod") (response/ok
   (selmer/render-file "topicmod.html" {:csrf (anti-forgery/anti-forgery-field)}))))

(defn mod-auth-handler [request]
  (if (hashers/check (get-mod-pwd request) (modpwd? (get-mod-topic request))) (init-mod-auth (response/see-other "/mod") request (get-mod-topic request)) 
      (response/ok (selmer/render-file "moderror.html" {}))))

(defn mod-handler [request]
  (if (is-mod-authenticated request)
    (response/ok
     (selmer/render-file "mod.html" {:csrf (anti-forgery/anti-forgery-field)}))
    (response/see-other "/topics/mod")))

(defn mod-ban-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-mod-authenticated request) (do 
                                         (banuser! (parse-int (get pars "banneduser")) (parse-int (get pars "banlength")) (get-topic-id-from-topic (get-mod-topic-auth request))) 
                                         (banusermsg! (parse-int (get pars "banneduser"))) (response/see-other "/mod")) (response/see-other "/error"))))

(defn mod-lock-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-mod-authenticated request) (do (lockthread! (parse-int (get pars "threadid")) (get-mod-topic-auth request)) (response/see-other "/mod")) (response/see-other "/error"))))

(defn mod-sticky-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-mod-authenticated request) (do (stickythread! (parse-int (get pars "threadid")) (get-mod-topic-auth request)) (response/see-other "/mod")) (response/see-other "/error"))))

(defn mod-del-thread-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-mod-authenticated request) (do (delthread! (parse-int (get pars "threadid")) (get-mod-topic-auth request)) (response/see-other "/mod")) (response/see-other "/error"))))

(defn mod-post-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-mod-authenticated request) 
      (if (mod-can-del-post? (parse-int (get pars "postid")) (get-topic-id-from-topic (get-mod-topic-auth request))) 
        (do (mod-deletepost! (parse-int (get pars "postid"))) (response/see-other "/mod")) 
        (response/ok (selmer/render-file "modfail.html" {})) ) (response/see-other "/error"))))

(defn mod-spoiler-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-mod-authenticated request)
      (if (mod-can-del-post? (parse-int (get pars "postid")) (get-topic-id-from-topic (get-mod-topic-auth request)))
        (do (mod-spoilerpost! (parse-int (get pars "postid"))) (response/see-other "/mod"))
        (response/ok (selmer/render-file "modfail.html" {}))) (response/see-other "/error"))))

(defn owner-panel-handler [request]
  (if 
   (is-owner-authenticated request) 
    (response/see-other "/owner") 
    (response/ok
     (selmer/render-file "topicowner.html" {:csrf (anti-forgery/anti-forgery-field)}))))

(defn owner-auth-handler [request]
  (if (hashers/check (get-owner-pwd request) (ownerpwd? (get-owner-topic request))) (init-owner-auth (response/see-other "/owner") request (get-owner-topic request))
      (response/ok (selmer/render-file "moderror.html" {}))))

(defn owner-handler [request]
  (if (is-owner-authenticated request)
    (response/ok
     (selmer/render-file "ownerpanel.html" {:csrf (anti-forgery/anti-forgery-field)}))
    (response/see-other "/topics/admin")))

(defn owner-password-change-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-owner-authenticated request)
      (do (ownerpassword! (hashers/derive (get pars "opassword")) (get-owner-topic-auth request)) (end-session-auth (response/see-other "/topics/admin")))
      (response/see-other "/topics/admin"))))

(defn owner-mod-password-change-handler [request]
  (let [pars (:multipart-params request)]
    (if (is-owner-authenticated request)
      (do (ownermodpassword! (hashers/derive (get pars "omodpassword")) (get-owner-topic-auth request)) (end-session-auth (response/see-other "/topics/admin")))
      (response/see-other "/topics/admin"))))

(defn serve-preview-handler [request]
  (let [pars (:params request) pid (:pid (:params request))]
    (let [tdata (filt-thread (preview-post? pid))]
    (response/ok (selmer/render-file "preview.html" {:tdata tdata})))
    ))