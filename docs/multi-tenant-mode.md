"Multi Tenant Mode" and "Master/Slave"

Overview:
The application, jukeanator-engine, will have two distinct modes of operation:
1: When it is running on https://jukeanator.com, headless (i.e. no UI), it will be considered the "master" and will need to be location-agnostic
   in that, it will not have "one" song library or song queue, rather, it will essentially be hosting the backend API 