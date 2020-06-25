# Change Log
All notable changes to this project will be documented in this file.

## 0.2.5 - 2020-06-24
- Removed autorefresh feature
- Updated Jquery datatables (security)

## 0.2.4 - 2020-03-25
- Set bump to be off by default as per bug #1
- Reduced catalog limit to 50 per board (feel free to customize if needed)
- Changed thread TTL mechanism: instead of being deleted, threads are now auto-locked if older than 6 months.
- Removed unique auto-tripcodes
- Added containerization (docker/docker-compose)
- Added 120 second thread page auto-refresh
- Changed flood time limit to 5 seconds, down from 20

## 0.2.3 - 2020-02-18
- Fixed mistake on readme systemd install instructions
- Fixed problem with abbreviation in index/catalog view causing certain tiles not to render
- Changed color scheme to red
- Fixed problem with individual post links not going far enough up page
- Fixed bug where quotes caused recent posts to not show up

## 0.2.2 - 2020-01-27
- Fixed quantifier issue with red text and spoilers
- Removed topic links
- Changed backlinks from >> to ##
- Implemented post preview on hover of backlink
- Fixed deprecated issue with uberjar aot compilation
- Changed autodelete threshold of threads to 60 days
- Updated README.md (copyright)

## 0.2.1 - 2019-12-23
- Fixed quantifier issue with topic links

## 0.2.0 - 2019-12-23
- Fixed Sage(no bump) functionality
- Fixed index/paginated view report button

## 0.1.9 - 2019-12-23
- Disabled single carrot quotes

## 0.1.8 - 2019-12-22
- Modified help page
- Wrap exception handling

## 0.1.7 - 2019-12-22
- Software no longer considered beta test
- Reports now are automatically deleted after 30 days
- Changed thread order in index and catalog to traditional latest reply bump mechanic
- Reports now go to a secondary page and use post instead of get
- Thread and index layout changed. Post score now displayed per post
- Thread indexes/catalogs now capped at 350 threads
- Implented single carrot quotes

## 0.1.6 - 2019-12-21 [Beta (Test) release 7]
- Fixed bug with triple image insert's third thumbnail being broken

## 0.1.5 - 2019-12-20 [Beta (Test) release 6]
- Fixed issue with long words, esp. hyperlinks breaking resposive on mobile resolutions
- Updated quotes to use a custom class instead of bulma box (remove rounded edges, shadows)
- Added shortcut formatting marks
- Added topic link formatting
- Added ability for mods to spoiler content
- Checkbox for 'agree to terms and conditions' is now automatically checked on replies
- Fixed title on terms/privacy page
- Updated generic terms and conditons

## 0.1.4 - 2019-12-18 [Beta (Test) release 5]
- Changed thumbnail functionality for faster page loading times
- Images open a full size version in a new tab when clicked.
- All images, including replies, are now automatically thumbnailed (if possible).
- Changed thumbnail resize method from bilinear to bicubic (higher quality)
- Fixed issued with certain GIF files erroring out on thumbnail (java ImageIO can't read certain gifs, so they aren't thumbnailed)
- Refactored insert-new-thread function for better readability

## 0.1.3 - 2019-12-17 [Beta (Test) release 4]
- Minor cosmetic fixes
- Changed catalog/index views to be based on weight sum rather than average.
- Fixed dark text issue on flood page and terms and conditions page
- Updated Installation instructions
- Updated setup page to force initial allowing of user-created topics

## 0.1.2 - 2019-12-13 [Beta (Test) release 3]
- Fixed catalog tile misalignment in Safari
- (Major) Various CSS changes to a dark/orange theme.
- Some minor text changes on help, topic page, etc.
- Replies now no longer have a border
- User ID's no longer in tag
- Changed link color to orange

## 0.1.1 - 2019-12-10 [Beta (Test) release 2]
- Fixed moderator "delete thread" functionality 404 error
- Changed quoted text background to dark grey for better visibility
- Changed index view and thread to show blue outline per post, with the exception of OP
- Added identing for reply posts
- Changed post background cycle to single shades of grey for better UX
- fixed issues with license in project.clj

## 0.1.0 - 2019-12-08 [Beta (Test) release 1]
- Initial release