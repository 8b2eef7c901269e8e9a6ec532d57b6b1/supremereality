(in-ns 'supremereality.core)

;; Database connection info (change the password)

(def db-spec {:dbtype "postgresql"
    :dbname "srdb"
    :user "sruser"
    :password "srpass"})

;;db functions

(defn db-schema-migrated?
    "Check if the schema has been migrated to the database"
    []
    (-> (jdbc/query db-spec
                   [(str "select count(*) from information_schema.tables "
                         "where table_name='meta'")])
        first :count pos?))

(def posts-table-ddl
    (jdbc/create-table-ddl :posts
                           [[:pid "bigserial" :primary :key]
                            [:post_time "timestamp not null" :default "Now()" ]
                            [:ipaddr "text"]
                            [:spoilered "boolean not null default false"]
                            [:msg "text"]
                            [:thid "bigint not null references threads(thid) ON DELETE CASCADE"]
                            [:weight :int :default 0]
                            [:attachmentone "bytea"]
                            [:attachmentonethumb "bytea"]
                            [:attachmentonetype "varchar(255)"]
                            [:attachmenttwo "bytea"]
                            [:attachmenttwothumb "bytea"]
                            [:attachmenttwotype "varchar(255)"]
                            [:attachmentthree "bytea"]
                            [:attachmentthreethumb "bytea"]
                            [:attachmentthreetype "varchar(255)"]]))

(def reports-table-ddl
  (jdbc/create-table-ddl :reports
                         [[:reportid "bigserial" :primary :key]
                          [:report_time "timestamp not null" :default "Now()"]
                          [:tid "int not null references topics(tid) ON DELETE CASCADE"]
                          [:thid "bigint not null references threads(thid) ON DELETE CASCADE"]
                          [:pid "bigint not null references posts(pid)"]]))
(def bans-table-ddl
  (jdbc/create-table-ddl :bans
                         [[:banid "bigserial" :primary :key]
                          [:tid "int not null references topics(tid) ON DELETE CASCADE"]
                          [:ban_start_time "timestamp not null" :default "Now()"]
                          [:ban_end_time "timestamp not null" :default "Now() + INTERVAL '1 DAY'"]
                          [:ipaddr "text not null"]]))

(def banned-topics-table-ddl
  (jdbc/create-table-ddl :bannedtopics
                         [[:btid "bigserial" :primary :key]
                          [:topicname "text not null"]]))

(def topics-table-ddl
    (jdbc/create-table-ddl :topics
        [[:tid "serial" :primary :key]
        [:topic "varchar(12) not null unique"]
        [:sfw "boolean not null"]
        [:bpwd "text not null"]
        [:bpwdmod "text"]
        [:bdesc "varchar(50)"]]))

(def meta-table-ddl
    (jdbc/create-table-ddl :meta
        [[:sitename "varchar(50) not null"]
         [:news "text"]
         [:ucreated "boolean not null"]
         [:apwd "text not null"]]))

(def threads-table-ddl
    (jdbc/create-table-ddl :threads
        [[:thid "bigserial" :primary :key]
        [:thread "varchar(32) not null"]
        [:thread_time "timestamp not null" :default "Now()" ]
        [:topic "int not null references topics(tid) ON DELETE CASCADE"]
        [:locked "boolean not null default false"]
        [:stickied "boolean not null default false"]]))

(defn site-data-dml [site-name site-pwd u-create]
    (do
        (jdbc/insert! db-spec :meta {:sitename site-name :apwd site-pwd :ucreated (boolean u-create)}) 
        (jdbc/insert! db-spec :topics {:topic "meta" :sfw true :bpwd site-pwd :bpwdmod site-pwd :bdesc "Meta Board"}) 
        (jdbc/execute! db-spec "CREATE OR REPLACE FUNCTION prune_threads()
                                RETURNS trigger AS
                                $BODY$
                                BEGIN
                                    update threads set locked=true where thid in (select z.thid from 
                                    (select threads.thid, count(posts.pid) pcnt from threads, posts where posts.thid = threads.thid and threads.locked <> true group by threads.thid) z where z.pcnt > 500);
                                    DELETE FROM THREADS WHERE THREAD_TIME < NOW() - INTERVAL '60 DAYS' and stickied <> true; 
                                    DELETE FROM reports WHERE report_time < NOW () - INTERVAL '30 DAYS';
                                    RETURN NEW;
                                END;
                                $BODY$
                                LANGUAGE plpgsql VOLATILE
                                COST 100;") 
        (jdbc/execute! db-spec "CREATE TRIGGER sched_thread_prune AFTER INSERT ON THREADS EXECUTE PROCEDURE prune_threads()")))

(defn topics? [] (jdbc/query db-spec ["SELECT topics.topic,
                                        CASE
                                            WHEN topics.sfw THEN 'SFW'
                                            ELSE 'NSFW'
                                        END AS sfw,
                                        topics.bdesc, 
                                        count(posts.pid) pcount,
                                        count(distinct threads.thid) tcount,
                                        count(distinct posts.ipaddr) ucount,
                                        trunc(coalesce(avg(posts.weight),0.0),2) qual
                                        FROM topics 
                                        left outer join threads on threads.topic=topics.tid and threads.locked <> true 
                                        left outer join posts on posts.thid=threads.thid 
                                        group by topics.tid order by qual desc"]))

(defn adminpwd? [] (:apwd (first (jdbc/query db-spec ["SELECT apwd FROM meta LIMIT 1"]))))
(defn boardinfo? [topicname] (:bdesc (first (jdbc/query db-spec ["select bdesc from topics where topic=?" topicname]))))

(defn prune-topic! [tid]
  (jdbc/execute! db-spec ["delete from threads where threads.thid in 
(select threads.thid
from threads,posts
where threads.thid=posts.thid
and threads.topic=(SELECT tid FROM TOPICS WHERE topic = ?)
and posts.weight is not null 
and posts.weight > 0
group by threads.thid
order by max (post_time) desc
offset 350)" (str tid)]))

(defn insert-new-thread
  ([threadname topicname msgbody ipaddr spoiled wgt]
   (jdbc/execute! db-spec ["WITH ins1 AS (
                            INSERT INTO threads(thread, topic)
                            VALUES (?, (SELECT tid FROM TOPICS WHERE topic = ?))
                            RETURNING thid AS threadid
                            ) INSERT INTO posts (thid, msg, ipaddr, spoilered, weight)
                            VALUES ((select threadid from ins1),?,?,?,?)" 
                           (str threadname) 
                           (str topicname) 
                           (parse-msg (str msgbody)) 
                           (str ipaddr) 
                           (boolean spoiled) 
                           wgt]))
  ([threadname topicname msgbody ipaddr spoiled wgt a1]
   (let [ftype (get-img-type a1)]
       (jdbc/execute! db-spec ["WITH ins1 AS (
                            INSERT INTO threads(thread, topic)
                            VALUES (?, (SELECT tid FROM TOPICS WHERE topic = ?))
                            RETURNING thid AS threadid
                            ) INSERT INTO posts (thid, msg, ipaddr, spoilered, weight, attachmentonetype, attachmentone, attachmentonethumb)
                            VALUES ((select threadid from ins1),?,?,?,?,?,?,?)" 
                               (str threadname) 
                               (str topicname) 
                               (parse-msg (str msgbody)) 
                               (str ipaddr) 
                               (boolean spoiled) 
                               wgt 
                               ftype 
                               (file->byte-array (:tempfile a1)) 
                               (thumbnail->img (:tempfile a1) ftype)])))
  ([threadname topicname msgbody ipaddr spoiled wgt a1 a2]
   (let [ftype (get-img-type a1)
         ftype2 (get-img-type a2)]
     (jdbc/execute! db-spec ["WITH ins1 AS (
                            INSERT INTO threads(thread, topic)
                            VALUES (?, (SELECT tid FROM TOPICS WHERE topic = ?))
                            RETURNING thid AS threadid
                            ) INSERT INTO posts (thid, msg, ipaddr, spoilered, weight, attachmentonetype, attachmentone, attachmentonethumb, attachmenttwotype, attachmenttwo, attachmenttwothumb)
                            VALUES ((select threadid from ins1),?,?,?,?,?,?,?,?,?,?)" 
                             (str threadname) 
                             (str topicname) 
                             (parse-msg (str msgbody)) 
                             (str ipaddr) 
                             (boolean spoiled) 
                             wgt 
                             ftype 
                             (file->byte-array (:tempfile a1)) 
                             (thumbnail->img (:tempfile a1) ftype) 
                             ftype2 
                             (file->byte-array (:tempfile a2)) 
                             (thumbnail->img (:tempfile a2) ftype2)])
     ))
  ([threadname topicname msgbody ipaddr spoiled wgt a1 a2 a3]
   (let [ftype (get-img-type a1)
         ftype2 (get-img-type a2)
         ftype3 (get-img-type a3)]
     (jdbc/execute! db-spec ["WITH ins1 AS (
                            INSERT INTO threads(thread, topic)
                            VALUES (?, (SELECT tid FROM TOPICS WHERE topic = ?))
                            RETURNING thid AS threadid
                            ) INSERT INTO posts (thid, msg, ipaddr, spoilered, weight, attachmentonetype, attachmentone, attachmentonethumb, attachmenttwotype, attachmenttwo, attachmenttwothumb, attachmentthreetype, attachmentthree, attachmentthreethumb)
                            VALUES ((select threadid from ins1),?,?,?,?,?,?,?,?,?,?,?,?,?)" 
                             (str threadname) 
                             (str topicname) 
                             (parse-msg (str msgbody)) 
                             (str ipaddr) 
                             (boolean spoiled) 
                             wgt 
                             ftype 
                             (file->byte-array (:tempfile a1)) 
                             (thumbnail->img (:tempfile a1) ftype) 
                             ftype2 
                             (file->byte-array (:tempfile a2)) 
                             (thumbnail->img (:tempfile a2) ftype2) 
                             ftype3 (file->byte-array (:tempfile a3)) 
                             (thumbnail->img (:tempfile a3) ftype3)]))))

;;more queries

(defn image1? [x] (jdbc/query db-spec ["SELECT attachmentone dimg, attachmentonetype dtype from posts where pid=?" x]))

(defn image2? [x] (jdbc/query db-spec ["SELECT attachmenttwo d2img, attachmenttwotype d2type from posts where pid=?" x]))

(defn image3? [x] (jdbc/query db-spec ["SELECT attachmentthree d3img, attachmentthreetype d3type from posts where pid=?" x]))

(defn thumbimg? [x] (jdbc/query db-spec ["SELECT attachmentonethumb dimg, attachmentonetype dtype from posts where pid=? and attachmentonethumb is not null" x]))

(defn thumbimg1? [x] (jdbc/query db-spec ["SELECT attachmenttwothumb d2img, attachmenttwotype d2type from posts where pid=? and attachmenttwothumb is not null" x]))

(defn thumbimg2? [x] (jdbc/query db-spec ["SELECT attachmentthreethumb d3img, attachmentthreetype d3type from posts where pid=? and attachmentthreethumb is not null" x]))

(defn thread? [x] (jdbc/query db-spec ["select post_time, pid, msg, ?*?||ipaddr ipaddr, attachmentonetype, attachmenttwotype, attachmentthreetype, spoilered, COALESCE(weight,0) qscore from posts where thid = ? order by pid" uuid-seed (parse-int x) (parse-int x)]))

(defn threadname? [x] (jdbc/query db-spec ["select thread from threads where thid = ?" (parse-int x)]))

(defn get-cata-link? [x] (jdbc/query db-spec ["select topics.topic from threads,topics where threads.thid=? and threads.topic=topics.tid" (parse-int x)]))
;

(defn thread-count? [x] (jdbc/query db-spec ["select count(1) tc from threads where thid=?" (parse-int x)]))

(defn topic-count? [x] (jdbc/query db-spec ["select count(1) tc from topics where topic=?" x]))

;; insert reply

(defn insert-new-reply
    ([threadid msgbody weight spoilered ipaddr]
        (jdbc/insert! db-spec :posts {:thid threadid :msg (parse-msg msgbody) :weight weight :spoilered spoilered :ipaddr ipaddr}))
    ([threadid msgbody weight spoilered ipaddr attachmentvec numattached]
        (cond 
            (= numattached 1) (let [fttype (get-img-type (first attachmentvec))] (if (or (= fttype "pdf") (= fttype "webm")) (jdbc/insert! db-spec :posts {:thid threadid 
                                                            :msg (parse-msg msgbody) 
                                                            :weight weight 
                                                            :spoilered spoilered 
                                                            :ipaddr ipaddr 
                                                            :attachmentonetype fttype 
                                                            :attachmentone (file->byte-array (:tempfile (first attachmentvec))) 
                                                            }) 
                                                            (jdbc/insert! db-spec :posts {:thid threadid 
                                                            :msg (parse-msg msgbody) 
                                                            :weight weight 
                                                            :spoilered spoilered 
                                                            :ipaddr ipaddr 
                                                            :attachmentonetype fttype 
                                                            :attachmentone (file->byte-array (:tempfile (first attachmentvec))) 
                                                            :attachmentonethumb (thumbnail->img (:tempfile (first attachmentvec)) fttype)
                                                            })
                                                            ))
            (= numattached 2) (jdbc/insert! db-spec :posts {:thid threadid 
                                                            :msg (parse-msg msgbody) 
                                                            :weight weight 
                                                            :spoilered spoilered 
                                                            :ipaddr ipaddr 
                                                            :attachmentonetype (get-img-type (first attachmentvec)) 
                                                            :attachmentone (file->byte-array (:tempfile (first attachmentvec))) 
                                                            :attachmentonethumb (if (and (not= (get-img-type (first attachmentvec)) "pdf") (not= (get-img-type (first attachmentvec)) "webm")) 
                                                                                    (thumbnail->img (:tempfile (first attachmentvec)) (get-img-type (first attachmentvec))) 
                                                                                    (file->byte-array (:tempfile (first attachmentvec))))
                                                            :attachmenttwotype (get-img-type (second attachmentvec)) 
                                                            :attachmenttwo (file->byte-array (:tempfile (second attachmentvec))) 
                                                            :attachmenttwothumb (if (and (not= (get-img-type (second attachmentvec)) "pdf") (not= (get-img-type (second attachmentvec)) "webm"))
                                                                                    (thumbnail->img (:tempfile (second attachmentvec)) (get-img-type (second attachmentvec))) 
                                                                                    (file->byte-array (:tempfile (second attachmentvec)))) 
                                                            })
            (= numattached 3) (jdbc/insert! db-spec :posts {:thid threadid
                                                            :msg (parse-msg msgbody)
                                                            :weight weight
                                                            :spoilered spoilered
                                                            :ipaddr ipaddr
                                                            :attachmentonetype (get-img-type (first attachmentvec))
                                                            :attachmentone (file->byte-array (:tempfile (first attachmentvec)))
                                                            :attachmentonethumb (if (and (not= (get-img-type (first attachmentvec)) "pdf") (not= (get-img-type (first attachmentvec)) "webm"))
                                                                                  (thumbnail->img (:tempfile (first attachmentvec)) (get-img-type (first attachmentvec)))
                                                                                  (file->byte-array (:tempfile (first attachmentvec))))
                                                            :attachmenttwotype (get-img-type (second attachmentvec))
                                                            :attachmenttwo (file->byte-array (:tempfile (second attachmentvec)))
                                                            :attachmenttwothumb (if (and (not= (get-img-type (second attachmentvec)) "pdf") (not= (get-img-type (second attachmentvec)) "webm"))
                                                                                  (thumbnail->img (:tempfile (second attachmentvec)) (get-img-type (second attachmentvec)))
                                                                                  (file->byte-array (:tempfile (second attachmentvec))))
                                                            :attachmentthreetype (get-img-type (nth attachmentvec 2))
                                                            :attachmentthree (file->byte-array (:tempfile (nth attachmentvec 2)))
                                                            :attachmentthreethumb (if (and (not= (get-img-type (nth attachmentvec 2)) "pdf") (not= (get-img-type (nth attachmentvec 2)) "webm"))
                                                                                  (thumbnail->img (:tempfile (nth attachmentvec 2)) (get-img-type (nth attachmentvec 2)))
                                                                                  (file->byte-array (:tempfile (nth attachmentvec 2))))
                                                            })
            :else nil)))

;;paginated queries

(defn get-paginated-thread [threadid]
    (jdbc/query db-spec ["select * from ((select threads.thread, attachmentonetype, attachmenttwotype, attachmentthreetype, posts.thid, pid, post_time, msg, spoilered, threads.locked, threads.stickied, case when threads.thread_time > now () - INTERVAL '2 DAYS' then TRUE else FALSE end as newtop from posts,threads where threads.thid=posts.thid and threads.thid=? order by pid limit 1)
                             union
                             (select threads.thread, attachmentonetype, attachmenttwotype, attachmentthreetype, posts.thid, pid, post_time, msg, spoilered, threads.locked, threads.stickied, case when threads.thread_time > now () - INTERVAL '2 DAYS' then TRUE else FALSE end as newtop from posts,threads where threads.thid=posts.thid and threads.thid=? order by pid desc limit 5)) x order by x.pid" threadid threadid]))

(defn get-threads-in-order [topicid page] 
    (let [os (* 10 page)]
        (jdbc/query db-spec ["select x.thid from (select threads.thid, threads.stickied, threads.locked, max(posts.post_time) g from threads,posts where threads.thid=posts.thid and threads.topic=? and posts.weight is not null and posts.weight > 0 group by threads.thid) x order by x.stickied desc, x.locked, x.g desc offset ? limit 10" topicid os])))

(defn get-topic-id-from-topic [topicname]
    (:tid (first (jdbc/query db-spec ["select tid from topics where topic=?" topicname]))))

(defn get-paginated-thread-count [topicname]
    (:tc (first (jdbc/query db-spec ["select (count(1)/10)+1 tc from threads where topic=?" topicname]))))

(defn get-site-name []
    (:sitename (first (jdbc/query db-spec ["select sitename from meta"]))))

(defn get-catalog-threads-in-order [topicid] 
    (jdbc/query db-spec ["select threads.thid from threads,posts where threads.thid=posts.thid and threads.topic=? and posts.weight is not null and posts.weight > 0 group by threads.thid order by threads.stickied desc, threads.locked, max(posts.post_time) desc" topicid]))

(defn get-catalog-thread [threadid]
    (jdbc/query db-spec ["select count(*) OVER (PARTITION BY threads.thid)-1 replies, 
count(posts.attachmentonetype) OVER (PARTITION BY threads.thid) + count(posts.attachmenttwotype) OVER (PARTITION BY threads.thid) + count(posts.attachmentthreetype) OVER (PARTITION BY threads.thid) imgcnt, 
posts.attachmentonetype, posts.msg, 
posts.thid, posts.pid, 
posts.post_time, 
posts.spoilered, 
threads.locked, 
threads.thread, 
threads.stickied,
case
when threads.thread_time > now () - INTERVAL '2 DAYS' then TRUE
else FALSE
end as newtop from posts,threads where posts.thid=threads.thid and posts.thid=? order by posts.pid limit 1" threadid]))

(defn get-topic-from-thread [threadid]
    (:topic (first (jdbc/query db-spec ["select topic from threads where thid=? limit 1" (parse-int threadid)]))))

(defn threadlocked? [threadid]
    (:locked (first (jdbc/query db-spec ["select locked from threads where thid=?" (parse-int threadid)]))))

(defn topicnames? []
  (jdbc/query db-spec ["select topic from topics where sfw=true order by topic"]))

(defn recentposts? []
  (jdbc/query db-spec ["select threads.thid, posts.pid, posts.msg, threads.thread, posts.attachmentonetype,topics.bdesc from posts,threads,topics
where posts.thid=threads.thid
and threads.topic=topics.tid
and posts.spoilered=false
and posts.attachmentone is not null
and posts.attachmentonethumb is not null
and posts.attachmentonetype <> 'webm'
and posts.attachmentonetype <> 'pdf'
and posts.weight is not null
and posts.weight > 0
and topics.sfw=true
order by posts.post_time desc
limit 8"]))

(defn site-stats? []
  (first (jdbc/query db-spec ["select count (posts.pid) totalposts, count (distinct ipaddr) currentusers, pg_size_pretty (pg_total_relation_size ('posts')) activecontent from posts"])))

(defn site-news? []
  (:news (first (jdbc/query db-spec ["select COALESCE (news,sitename) news from meta"]))))

(defn sitename! [newsitename]
  (jdbc/update! db-spec :meta {:sitename newsitename} []))

(defn ucreated! []
  (if (= (:ucreated (first (jdbc/query db-spec ["select ucreated from meta"]))) true)
    (jdbc/update! db-spec :meta {:ucreated false} []) (jdbc/update! db-spec :meta {:ucreated true} [])))

(defn ucreated? []
  (:ucreated (first (jdbc/query db-spec ["select ucreated from meta"]))))

(defn sitenews! [news]
  (jdbc/update! db-spec :meta {:news news} []))

(defn site-news-edit? []
  (escape-html (:news (first (jdbc/query db-spec ["select COALESCE (news,sitename) news from meta"])))))

(defn adminpassword! [newpass]
  (jdbc/update! db-spec :meta {:apwd newpass} []))

(defn insert-topic [tname tdesc tpass tnsfw]
  (jdbc/insert! db-spec :topics {:sfw tnsfw :topic tname :bdesc tdesc :bpwd tpass :bpwdmod tpass}))

(defn get-topic-name-from-topic [topicid]
  (:topic (first (jdbc/query db-spec ["select topic from topics where tid=?" topicid]))))

(defn insert-report! [tid thid pid]
  (jdbc/insert! db-spec :reports {:tid tid :thid thid :pid pid}))

(defn existingtopic? [topicname]
  (if (> (:cnt (first (jdbc/query db-spec ["select count(topic) cnt from topics where topic=?" topicname]))) 0) false true))

(defn bantopic! [topicname]
  (do
    (jdbc/insert! db-spec :bannedtopics {:topicname topicname}) (jdbc/delete! db-spec :topics ["topic = ?" topicname])))

(defn bannedtopics? [] (jdbc/query db-spec ["select topicname from bannedtopics order by btid desc limit 20"]))

(defn bannedtopic? [topname]
  (if (> (:tc (first (jdbc/query db-spec ["select count(topicname) tc from bannedtopics where topicname=?" topname]))) 0) false true))

(defn globalreports? [] (jdbc/query db-spec ["select reportid,report_time,tid,thid,pid from reports where report_time > Now () - INTERVAL '1 MONTH'"]))

(defn deletepost! [postid]
  (jdbc/update! db-spec :posts {:msg "[red]POST DELETED[/red]" :attachmentone nil :attachmenttwo nil :attachmentthree nil :attachmentonetype nil :attachmenttwotype nil :attachmentthreetype nil :attachmentonethumb nil :attachmenttwothumb nil :attachmentthreethumb nil :weight nil} ["pid=?" (parse-int postid)]))

(defn deletepost? [postid ipaddr]
  (if 
   (> (:cnt (first (jdbc/query db-spec ["select count(pid) cnt from posts where pid=? and ipaddr=? and post_time > Now() - INTERVAL '1 HOUR'" (parse-int postid) ipaddr]))) 0) 
    true 
    false))

(defn modpwd? [topicn] (:bpwdmod (first (jdbc/query db-spec ["SELECT bpwdmod FROM topics where topic=?" topicn]))))

(defn postip? [postid] (:ipaddr (first (jdbc/query db-spec ["select ipaddr from posts where pid=?" postid]))))

(defn banusermsg! [postid]
  (jdbc/execute! db-spec ["update posts set msg=msg||'[br][br][red]⚠️ USER BANNED FOR THIS POST[/red]' where pid=?" postid]))

(defn banuser! [postid banlength topicid]
  (jdbc/insert! db-spec :bans {:ban_end_time (time/sql-timestamp (time/plus (time/local-date-time) (time/days banlength))) :ipaddr (postip? postid) :tid topicid}))

(defn lockthread! [threadid topicn]
  (jdbc/execute! db-spec ["update threads set locked= NOT locked where thid=? and topic=?" threadid (get-topic-id-from-topic topicn)]))

(defn stickythread! [threadid topicn]
  (jdbc/execute! db-spec ["update threads set stickied= NOT stickied where thid=? and topic=?" threadid (get-topic-id-from-topic topicn)]))

(defn delthread! [threadid topicn]
  (jdbc/execute! db-spec ["delete from threads where thid=? and topic=?" threadid (get-topic-id-from-topic topicn)]))

(defn mod-deletepost! [postid]
  (jdbc/execute! db-spec ["update posts set msg='[red]⚠️ POST DELETED (MOD)[/red]',attachmentone=null,attachmenttwo=null,attachmentthree=null,attachmentonethumb=null,attachmenttwothumb=null,attachmentthreethumb=null,weight=1,attachmentonetype=null,attachmenttwotype=null,attachmentthreetype=null where pid=?" postid]))

(defn mod-can-del-post? [postid topicid]
  (if (= (:tid (first (jdbc/query db-spec ["select topics.tid from posts,threads,topics where posts.thid=threads.thid and threads.topic=topics.tid and pid=?" postid]))) topicid) true false))

(defn ownerpwd? [topicn] (:bpwd (first (jdbc/query db-spec ["SELECT bpwd FROM topics where topic=?" topicn]))))

(defn ownerpassword! [newpass topicn]
  (jdbc/update! db-spec :topics {:bpwd newpass} ["topic = ?" topicn]))

(defn ownermodpassword! [newpass topicn]
  (jdbc/update! db-spec :topics {:bpwdmod newpass} ["topic = ?" topicn]))

(defn is-user-banned? [ipaddr tid]
  (:endban (first (jdbc/query db-spec ["select max(ban_end_time) endban from bans where ipaddr=? and ban_end_time > Now() and tid=?" ipaddr tid]))))

(defn mod-spoilerpost! [postid]
  (jdbc/execute! db-spec ["update posts set spoilered=true where pid=?" postid]))

(defn preview-post? [pid] 
  (jdbc/query db-spec ["select post_time,  
pid,
msg, 
attachmentonetype, 
attachmenttwotype, 
attachmentthreetype, 
spoilered
from posts where pid = ?" (parse-int pid)]))