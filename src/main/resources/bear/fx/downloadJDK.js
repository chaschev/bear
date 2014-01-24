var clickIt = function($el){
  var el = $el[0];
  var etype = 'click';

  clickDom(el, etype);
}

var clickDom = function(el, etype){
  if (el.fireEvent) {
//    alert('step 2');
    el.fireEvent('on' + etype);
  } else {
//    alert('step 3');
//    alert(document.createEvent);

    var evObj = document.createEvent('Events');

    evObj.initEvent(etype, true, false);
//    alert('step 5');
//    alert(el.dispatchEvent);

    el.dispatchEvent(evObj);
  }
}

var find = function(version, x64, platform){
    var r = [null];

    jQuery("div.lic_div").each(function(index, el){
        var $table = jQuery(el).closest('table');
        $links = $table.find('a[id^=jdk-][href]');

        $links.each(function(index, linkEl){
            var $a = jQuery(linkEl);
            var name = $a.attr('id');

            //console.log(name, $a);

            alert("checking " + name);

            if(name.indexOf('.rpm') == -1 && name.indexOf('-rpm') == -1
                && name.indexOf(version) != -1
                && name.indexOf(platform) != -1){
                if(x64){
                    if(name.indexOf('x64') != -1){
                        r[0] = $a;
                        alert("found: " + $a);
                        return false;
                    }
                }else{
                    if(name.indexOf('i586') != -1){
                        r[0] = $a;
                        alert("found: " + $a);
                        return false;
                    }
                }
            }
        });

        if(r[0]) {
            $table.find('input:first').click();
            alert("found link: " + r[0]);
            return false;
        }
    });

    alert("returning r: " + r[0]);

    return r[0];
};

//find('7u45', true, 'linux');

var downloadIfFound = function(version, x64, platform){
    var $a = find(version, x64, platform);

    if($a){
        clickIt($a);
        return true;
    }

    alert("download has: " + $a);

    return false;
}

