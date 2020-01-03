(in-ns 'supremereality.core)

(defn init-setup-auth [resmap reqmap]
    (let [tsession (:session reqmap)]
        (let [nsession (assoc tsession :setup true)]
            (assoc resmap :session nsession))))

(defn end-setup-auth [resmap reqmap]
    (let [tsession (:session reqmap)]
        (let [nsession (dissoc tsession :setup)]
            (assoc resmap :session nsession))))

(defn is-setup-authenticated [reqmap]
    (let [tsession (:session reqmap)]
        (let [authd (:setup tsession)]
            (if (boolean authd) true false))))

(defn get-adm-pwd [reqmap]
    (get (:form-params reqmap) "post-apwd" ))

(defn init-admin-auth [resmap reqmap]
    (let [tsession (:session reqmap)]
        (let [nsession (assoc tsession :admin true)]
            (assoc resmap :session nsession))))

(defn is-admin-authenticated [reqmap]
    (let [tsession (:session reqmap)]
        (let [authd (:admin tsession)]
            (if (boolean authd) true false))))

(defn end-session-auth [resmap]
    (assoc resmap :session nil))

(defn get-client-ip [request]
  (if-let [ips (get-in request [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr request)))

(defn scale [img ratio width height]
    (let [scale (AffineTransform/getScaleInstance
                    (double ratio) (double ratio))
                    
                    transform-op (AffineTransformOp.
                    scale AffineTransformOp/TYPE_BICUBIC)]
    (.filter transform-op img (BufferedImage. width height (.getType img)))))

(def thumb-size 150)
(def thumb-prefix "thumb_")

(defn scale-image 
  ([file thumb-size]
  (let [img (ImageIO/read file)
        img-width (.getWidth img)
        img-height (.getHeight img)
        ratio (/ thumb-size img-height)]
    (try 
      (scale img ratio (int (* img-width ratio)) thumb-size)
      (catch Exception e 
        img)))))

(defn file->byte-array [x]
    (with-open [input (FileInputStream. x)
                buffer (ByteArrayOutputStream.)]
        (clojure.java.io/copy input buffer)
        (.toByteArray buffer)))

(defn image->byte-array [image imgtype]
    (let [baos (ByteArrayOutputStream.)]
        (ImageIO/write image imgtype baos)
        (.toByteArray baos)))

(defn thumbnail->img [file ftype]
  (if (and (not= ftype "webm") (not= ftype "pdf"))
    (try (image->byte-array (scale-image file thumb-size) ftype) (catch Exception e (file->byte-array file)))
    nil))

(defn get-img-type [rmap]
    (let [contenttype (:content-type rmap)]
        (second (re-matches #".*/(png|jpeg|gif|pdf|webm)" contenttype))))

(defn non-empty-attachments [a1 a2 a3]
    (filter 
        (fn [x] (> (:size x) 0)) (vector a1 a2 a3)))

(defn num-non-empty-attachments [a1 a2 a3]
    (count (filter 
        (fn [x] (> (:size x) 0)) (vector a1 a2 a3))))

(defn weight-calc [msgbody] 
"calculates the quality of a post"
    (let [dstring (str/split (str/replace msgbody #"(((http|https)://)(www[.]){0,1}[-a-zA-Z0-9]+[.]([.a-zA-Z]){2,64}[^\s]*)" "") #" ")]
        (let [dcount (count dstring)]
            (if (not= dcount 0) 
                (let [daverage (double (/ (reduce + (map count (filter (fn [x] (< (count x) 45)) dstring))) dcount))] 
                    (/ (* daverage daverage dcount) 10)) 0 ))))

(defn parse-int [s]
    (Integer/parseInt (re-find #"\A-?\d+" s)))

(defn change-keys [x] 
    (conj 
        (cond 
            (= (:attachmentonetype x) "pdf") {:attachmentonepdf "pdf"}
            (= (:attachmentonetype x) "webm") {:attachmentonewebm "webm"}
            :else {:attachmentonetype (:attachmentonetype x)}) 
        (cond 
            (= (:attachmenttwotype x) "pdf") {:attachmenttwopdf "pdf"}
            (= (:attachmenttwotype x) "webm") {:attachmenttwowebm "webm"}
            :else {:attachmenttwotype (:attachmenttwotype x)})    
        (cond 
            (= (:attachmentthreetype x) "pdf") {:attachmentthreepdf "pdf"}
            (= (:attachmentthreetype x) "webm") {:attachmentthreewebm "webm"}
            :else {:attachmentthreetype (:attachmentthreetype x)})  
    {})
)

(defn filt-thread [z]
    (map (fn [x] (let [attachd (select-keys x [:attachmentonetype :attachmenttwotype :attachmentthreetype])
          prefx (dissoc x :attachmentonetype :attachmenttwotype :attachmentthreetype)]
          (conj prefx (change-keys attachd)))) z))

(defn escape-html
  "Change special characters into HTML character entities."
  [text]
  (.. #^String (str text)
    (replace "&" "&amp;")
    (replace "<" "&lt;")
    (replace ">" "&gt;")
    (replace "\"" "&quot;")))

(defn strip-preview [msg]
  (str/replace msg #"\[preview\](.*)\[\\preview\]" ""))

(defn parse-url [msg]
    (str/replace msg #"((https://)(www[.]){0,1}[-a-zA-Z0-9]+[.]([.a-zA-Z]){2,64}[^\s]*)" "[link]$1[/link]$1[/elink]"))

(defn parse-shortr [msg]
  (str/replace msg #"((==)(.*?)(==))" "[red]$3[/red]"))

(defn parse-shortr2 [msg]
  (str/replace msg #"((&lt;)(.*))" "[red]$3[/red]"))

(defn parse-shortsp [msg]
  (str/replace msg #"((\*\*)(.*?)(\*\*))" "[spoiler]$3[/spoiler]"))

(defn parse-shortq [msg]
  (str/replace msg #"(&gt;)(.*)" "[quote]$2[/quote]"))

(defn parse-newline [msg]
    (str/replace msg #"(\n)" "[br]"))

(defn parse-quote [msg]
  (str/replace msg #"##([0-9]+)" "[qlink]#$1[/qlink]##$1[/qelink][preview]$1[/preview]"))

(defn parse-msg [msg] (parse-url (parse-newline (parse-quote (parse-shortsp (parse-shortr (parse-shortr2 (parse-shortq (escape-html msg)))))))))

(defn get-mod-pwd [reqmap]
  (get (:form-params reqmap) "modpwd"))

(defn get-mod-topic [reqmap]
  (get (:form-params reqmap) "topicmodname"))

(defn init-mod-auth [resmap reqmap topicn]
  (let [tsession (:session reqmap)]
    (let [nsession (assoc tsession :mod topicn)]
      (assoc resmap :session nsession))))

(defn is-mod-authenticated [reqmap]
  (let [tsession (:session reqmap)]
    (let [authd (:mod tsession)]
      (if (nil? authd) false true))))

(defn get-mod-topic-auth [reqmap]
  (let [tsession (:session reqmap)]
    (let [authd (:mod tsession)]
      (if (nil? authd) nil (str authd)))))

(defn get-owner-pwd [reqmap]
  (get (:form-params reqmap) "ownerpwd"))

(defn get-owner-topic [reqmap]
  (get (:form-params reqmap) "topicownername"))

(defn init-owner-auth [resmap reqmap topicn]
  (let [tsession (:session reqmap)]
    (let [nsession (assoc tsession :owner topicn)]
      (assoc resmap :session nsession))))

(defn is-owner-authenticated [reqmap]
  (let [tsession (:session reqmap)]
    (let [authd (:owner tsession)]
      (if (nil? authd) false true))))

(defn get-owner-topic-auth [reqmap]
  (let [tsession (:session reqmap)]
    (let [authd (:owner tsession)]
      (if (nil? authd) nil (str authd)))))

(defn detect-flooduser [handler reqmap]
  (let [clientip (get-client-ip reqmap)] 
      (if 
       (< (compare (get @floodlist clientip) (time/minus (time/local-date-time) (time/seconds time2flood))) 0) 
        (do (swap! floodlist assoc clientip (time/local-date-time)) (handler reqmap)) 
        {:status 200 :body (selmer/render-file "flood.html" {:waittime time2flood})})))

(defn reset-floodlist []
  (reset! floodlist {}))