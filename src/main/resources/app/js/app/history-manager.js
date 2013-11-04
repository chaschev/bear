/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

function extend(sub, base) {
    // Avoid instantiating the base class just to setup inheritance
    // See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/create
    // for a polyfill
    sub.prototype = Object.create(base.prototype);
    // Remember the constructor property was set wrong, let's fix it
    sub.prototype.constructor = sub;
    // In ECMAScript5+ (all modern browsers), you can make the constructor property
    // non-enumerable if you define it like this instead
    Object.defineProperty(sub.prototype, 'constructor', {
        enumerable: false,
        value: sub
    });
}

var AbstractHistoryEntry = function(){
    this.type = 'abs';
    this.time = new Date().getTime();
};

AbstractHistoryEntry.prototype.getTime = function(){
    console.log('getTime', this.time);

    return this.time;
};

AbstractHistoryEntry.prototype.getText = function(fileManager){
    throw "not implemented!";
};

function removeComment(s){
    if(s[0] == '#'){
        s = s.substring(1).trim();
    }

    if(s.length > 50){
        s = s.substring(0, 50) + '...';
    }

    return s;
}
AbstractHistoryEntry.prototype._initNameAndDesc = function (text) {
    if(!text){
        console.log("warning, text: ", text);

        this.name = '';
        this.desc = '';

        return;
    }

    console.log('text: ', text);

    var firstLine = text.indexOf('\n');
    if (firstLine == -1) firstLine = text.length;

    var secondLine = text.indexOf('\n', firstLine + 1);
    if (secondLine == -1) secondLine = text.length;

    this.name = removeComment(text.substring(0, firstLine));
    this.desc = firstLine + 1 < secondLine ? removeComment(text.substring(firstLine + 1, secondLine)) : '';
};

AbstractHistoryEntry.prototype.computeText = function(fileManager){
    throw "abstract!";
};

AbstractHistoryEntry.prototype.getText = function(fileManager){
    if(this.text == null){
        this.text = this.computeText(fileManager);
    }

    return this.text;
};

AbstractHistoryEntry.prototype.getName = function(fileManager){
    if(this.name == null){
        this._initNameAndDesc(this.getText(fileManager));
    }

    return this.name;
};

AbstractHistoryEntry.prototype.getDesc = function(fileManager){
    if(this.name == null){
        this._initNameAndDesc(this.getText(fileManager));
    }

    return this.desc;
};

var FileReferenceHistoryEntry = function(file){
    AbstractHistoryEntry.call(this);

    this.file = file;
    this.type = 'file';
    console.log('FileEntry.<init>, file:', this.file);
};

extend(FileReferenceHistoryEntry, AbstractHistoryEntry);

FileReferenceHistoryEntry.prototype.computeText = function(fileManager){
    console.log('FileEntry.computeText, file:', this.file, fileManager);

    return fileManager.readFileByPath(this.file);
};


var ScriptHistoryEntry = function(script){
    this.text = script;
    this.type = 'script';
};

extend(ScriptHistoryEntry, AbstractHistoryEntry);