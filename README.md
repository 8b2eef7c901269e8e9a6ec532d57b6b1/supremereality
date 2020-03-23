# Supreme Reality

An imageboard written using Clojure, Javascript, Bulma.css and PostgreSQL.

# Usage

DEMO SITE: https://www.supremereality.us/

Supreme Reality is an imageboard, which is a type of anonymous internet forum based on the sharing of images and text. 

# Automatic Installation (Containerized)

## This requires you to install docker and docker-compose

1. Clone this repo into a folder on the machine
2. Naviate to that folder
3. type 'docker-compose up --build -d'

That's it. For future deployments you can take off the --build flag if you didn't change anything and don't need to rebuild the container.
To stop the app, type 'docker-compose down'.

# Manual Installation

## Pre-installation

Make sure that your domain name is pointing to your application server

I assume the user is familiar with Linux, and the Linux command line.

### These steps can be done using apt-get or whatever package manager your distro has

1. Install Java 8+ if not installed yet
2. Install Nginx
3. Install PostgreSQL
4. Install Leiningen (Clojure build tool)

## Configuration

1. Download the source code by doing a git clone to this repo
2. Modify any settings you want to change in the code (i.e. database username/password, flood interval, upload limit or any other settings)
2. Navigate to the top level folder and type "lein uberjar" - this compiles the program into a jar (may take a while)
3. Run jar as a service

## Firewall (Ubuntu)

Ubuntu's built in firewall is called 'Uncomplicated Firewall' or 'UFW' for short. It should already come with a default install. However you can also use apt-get to install it if it is missing. Once/if it is downloaded/installed, run the following commands to set it up:

First, check to see if it is running:

`sudo ufw status verbose`

If so, disable it using:

`sudo ufw disable`

Set up the rules:

```
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 'Nginx Full'
sudo ufw allow 3000/tcp
```

Then enable it using:

`sudo ufw enable`

## Options for your imageboard

There are some options you can and should set in the code before running it in production.

- Database Options

At the top of the file: src/database.clj, the default setup looks like this:

```
(def db-spec {:dbtype "postgresql"
    :dbname "srdb"
    ;; :host "172.20.0.2" ;;comment this line out on non-docker/manual installations
    :user "sruser"
    :password "srpass"})
```

You should change the schema, user, and password to whatever you've set it up to be in Postgres. Put a ; semicolon in front of the :host parameter to comment it out.
This exists for docker installs only.
I'm not going to go over how to set up Postgres here as there are plenty of guides online on how to do this (use google).

### UUID Seed

Under src/core.clj you will a line that says

```
;;UUID seed (gives your users a unique id, prefer a prime number)
(def uuid-seed 15485857)
```

This is a seed that gives your users (mostly) unique per-user, per-thread ids.
Please change this from the default to any other number, preferably prime number.

### Flood protection

Also Under src/core.clj you will a line that says

```
;;time in between posts (in seconds)
(def time2flood 20)
```

This is the time in seconds your users have to wait between posts globally. The default is 20 seconds.
This is usually ok, but if you are having problems with bots/spammers you may want to set it higher.

### Upload Limit

Under the main program in src/core.clj you will see an option that says

`:max-body 20000000`

This represents the maximum amount of bytes a POST request can have. This includes all text of the post, and all attachments.
The default is 20000000 bytes which is equal to 20 megabytes. You can set this higher or lower depending on preference.

- For obvious reasons please change any of these options before compiling. If you want to change them after, you will have to recompile for them to take effect.

## Basic Example on Ubuntu 18.04

NOTE: If you are using a different distribution of linux this part will be different! read the documentation!
Placeholders like myimgboard, sampleuser, the execstart target should be substituted here depending on what you are calling your app/site

put a file similar to this at /etc/systemd/system/myimgboard.service

```
[Unit]
Description=My Imageboard Service
[Service]
User=sampleuser
#change this to your workspace
WorkingDirectory=/home/sampleuser/workspace
#path to executable. 
#executable is a bash script which calls jar file
ExecStart=/home/sampleuser/startscript
SuccessExitStatus=143
TimeoutStopSec=10
Restart=on-failure
RestartSec=5
[Install]
WantedBy=multi-user.target
```

Create a shell script to run your jar (name it srstart or something - no need for a .sh extension)

```
#!/bin/sh
sudo /usr/bin/java -jar /home/sampleuser/supremereality/target/supremereality-0.1.3-standalone.jar
```

Give the jar executable permissions with:

`chmod a+x /home/sampleuser/supremereality/target/supremereality-0.1.3-standalone.jar`

Start the service

```
sudo systemctl daemon-reload
sudo systemctl enable myimgboard.service
sudo systemctl start myimgboard
```

Use the follow command to check to see that your service is running

`sudo systemctl status myimgboard`

Your site should be running on port 3000 of the machine. If it isn't running you messed up somewhere. Make sure you gave the necessary executable permissions.
It is also possible, if you have a firewall, that port 3000 is being blocked.

## Nginx

After installing Nginx you need to set up the ability to reverse proxy the app

### Set up 'sites available'

Create the file at: /etc/nginx/sites-available/yoursite.com

Example (similar to this):

```
server{
  listen 80 default_server;
  listen [::]:80 default_server;            
  server_name localhost yoursite.com www.yoursite.com;

  access_log /var/log/myimgboard_access.log;
  error_log /var/log/myimgboard_error.log;

  location / {
    proxy_pass http://localhost:3000/;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_redirect  off;
  }
}
```

Then, you have to link it like this:

`sudo ln -s /etc/nginx/sites-available/yoursite.com /etc/nginx/sites-enabled/`

Test the configuration of Nginx with this command:

`sudo nginx -t`

If it says it's ok, then feel free to restart Nginx using:

`sudo systemctl restart Nginx`

Your changes should take effect. At this point you should be able to access the app from both port 3000 as well as the domain url.
However, there is still merely an error page. This is because you have not run through the setup wizard.

## Encrypting with 'Let's Encrypt'

It's current year and there is no excuse not to be using HTTPS/SSL/TLS encryption on your website.

### Download certbot

`sudo add-apt-repository ppa:certbot/certbot`

### Do an apt-get update

`sudo apt-get update`

### Download certbot for Nginx

`sudo apt-get install python-certbot-nginx`

### Get your SSL certificate

`sudo certbot --nginx -d yoursite.com -d www.yoursite.com`

At this stage it will ask you if you want HTTPS redirect. You should choose HTTPS redirect (2).
This will essentially force all users of your site to use the encrypted version, which is a good thing.

It may also ask you for an email for the EFF or something in the process, you can decline that part if you want.

## Setup Wizard

Navigate to yoursite.com/setup. You should see a page with a single button on it. Before you run the setup page, make sure you accurately input the correct database name and password into database.clj in the 'src' folder. If you have the database info wrong, the next page will simply error out.

Click the button. If everything goes good, all the tables in the database necessary for your imageboard should be set up.

At this stage you should come to a page that instructs you to set up an admin password, name of website, etc. Please fill out these forms then process by clicking the next button.

At this stage you should see a page that confirms that you have completed setup. Your website is now ready to use.

## Common Problems

### I'm getting an error message that says Request Entity too large!

This means that the upload limit on Nginx is lower than your app. 

Edit the file /etc/nginx/nginx.conf

Add the following line under http (or change line if it exists)

`client_max_body_size 19M;`

Set the amount allowed to 1 megabyte below your app's limit to be safe.

### I'm getting an error message that says bad gateway!

This means that Nginx is running but it's not connecting to your app. This can happen because Nginx starts up faster than your app, in which case you just have to wait a minute or two for it to work. If you keep getting the error message after 5+ minutes, there is likely something wrong with your Nginx config.

## Updating and Migrating

I haven't made any breaking updates yet. If you don't mind redoing all of your options in the source (seed, etc.) You can simply stop the service, re-clone the repo, and edit your options back in. 

### Stop Service

`sudo systemctl stop myimgboard.service`

- delete app folder using `rm -rf supremereality`
- git clone (this repo)
- apply any options
- update start shell script (the default .jar generated may have a different filename due to higher version, so you may have to change execstart target slightly)

### Restart Service

`sudo systemctl start myimgboard.service`

### Check to see it's working

`sudo systemctl status myimgboard.service`

Alternatively, keep your own settings and do a git pull.
You will have to do a source control merge at this point between my updates and your custom settings.

# License

Copyright © 2019-2020 Jared Schreiber

This program and the accompanying materials are made available under the
terms of the 3-Clause BSD license which is available at
https://opensource.org/licenses/BSD-3-Clause.
