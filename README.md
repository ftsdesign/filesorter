# File Sorter

File Sorter is a tool for preparing a uniform file data set.

It takes a collection of folders and recursively puts all the files from these folders 
into an output folder, changing the file names to random 
UUID string, and sorting them into 16 [0-9a-f] subfolders, according to their UUID prefix.

For each file, after it has been placed into the target directory, FileSorter can:
* Run arbitrary bash command
* Reset the timestamp to a user defined value
