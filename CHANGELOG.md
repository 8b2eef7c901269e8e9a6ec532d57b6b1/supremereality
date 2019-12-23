# Change Log
All notable changes to this project will be documented in this file.

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