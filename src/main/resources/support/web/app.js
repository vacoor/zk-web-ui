/**
 * Zookeeper urls configuration.
 * <p>
 *     1. Gets from url parameters 'urls'.
 *     2. Using 'DEFAULT_ZK_URLS'
 * </p>
 | Operation     |  Scope           | Description                |
 | ------------- | ---------------- | -------------------------- |
 |  Alt + E      | Global           | Open 'Expand' window       |
 |  ESC          | Global           | Close 'Expand' window      |
 |  Alt + R      | Tree             | Refresh the selected node  |
 |  Alt + D      | Tree             | Dump the selected node     |
 |  Alt + Insert | Table            | Create a new leaf(node)    |
 |  Alt + Delete | Table            | Delete the selected leaf   |
 |  Double click | Table cell value | Edit the clicked value     |
 |  Alt + S      | Global           | Save all unsaved leaf data |
 |  Alt + 1      | Global           | Tree focus                 |

 */
var ZK_ALL_URLS = (function (matches) {
    var DEFAULT_ZK_URLS = [/* TODO configure 'Zookeeper server urls' */];
    return matches ? decodeURIComponent(matches[2]).split(/,/) : DEFAULT_ZK_URLS;
})(window.location.search.substr(1).match(/(^|&)urls=([^&]*)(&|$)/i));

(function (ZK_URLS, Shortcut) {
    function decode(hex) {
        try {
            return /^0x[0-9a-zA-Z]+$/.test(hex) && decodeURIComponent(hex.substring(2).replace(/(.{2})/g, '%$1'));
        } catch (err) {
            // ignore
        }
        return null;
    }

    function expandTo($jstree, path) {
        function doExpand($jstree, path, childs) {
            var openPath = path ? path : '/' + childs.shift();
            var child = (childs || []).shift();
            if (child) {
                $jstree.open_node(openPath, function (node, opened) {
                    $jstree.deselect_all();
                    $jstree.select_node(node);
                    $jstree.get_node(node, true).focus()[0].scrollIntoView();
                    doExpand($jstree, openPath + '/' + child, childs);
                });
            } else {
                if ($jstree.get_node(openPath, true)) {
                    $jstree.deselect_all();
                    $jstree.select_node(openPath);
                    $jstree.get_node(openPath, true).focus()[0].scrollIntoView();
                }
            }
        }

        var childs = (path || '').split('/');
        doExpand($jstree, childs.shift(), childs);
    }

    ZK_URLS = '[object Array]' === Object.prototype.toString.call(ZK_URLS) ? ZK_URLS : [ZK_URLS];
    if (!ZK_URLS || 1 > ZK_URLS.length) {
        jQuery('.input-box').removeClass('hide');
        // window.confirm('ZK_URLS is not configured and "urls" parameter is missing !!');
        throw 'ZK_URLS is not configured and "urls" parameter is missing !!';
    }

    var $viewport = $('.zk-ui-view'),
        $tbody = $viewport.find('.leaf-table tbody'),
        $statusbar = $viewport.find('.statusbar .text');

    /* nodes - nav - tree [[ */
    var $jstree = $('#tree').on('ready.jstree', function () {
        /*! on ready, select and open first node. */
        var instance = $.jstree.reference(this), first = instance.get_children_dom('#').first();
        instance.select_node(first);
        instance.open_node(first);
        instance.get_container().focus();
        $viewport.addClass('loaded');
    }).on("focus.jstree", ".jstree-anchor", function (event) {
        /*! single select. */
        var node = event.target, instance = $.jstree.reference(node);
        if (!instance.is_disabled(node)) {
            instance.deselect_all();
            instance.select_node(node);
        }
        return false;
    }).on('select_node.jstree refresh_node.jstree', function (event, data) {
        var instance = $.jstree.reference(this);

        function cb(node, loaded) {
            var n = node.original, value = n.value, decoded = decode(value), leafs = n.leafs || [], i,
                root = node.parents[node.parents.length - 2] || node.id;
            value = n.length < 0 ? '(数据未设置)' : (0 === n.length ? '(空)' : (decoded ? decoded : value));

            $tbody.children().remove();
            if ('#' !== node.parent) {
                $('<tr data-type="parent" data-id="' + n.id + '" data-path="' + n.path + '" data-root="' + root + '"><td><i class="icon icon-txt"></i>(默认)</td><td>' + n.type + '</td><td class="col-value">' + value + '</td></tr>').appendTo($tbody);
            }

            for (i = 0; i < leafs.length; i++) {
                value = leafs[i].value;
                decoded = decode(value);
                value = leafs[i].length < 0 ? '(数据未设置)' : (0 === leafs[i].length ? '(空)' : (decoded ? decoded : value));
                $('<tr data-id="' + leafs[i].id + '" data-path="' + leafs[i].path + '" data-root="' + root + '"><td><i class="icon icon-txt"></i>' + leafs[i].text + '</td><td>' + leafs[i].type + '</td><td class="col-value">' + value + '</td></tr>').appendTo($tbody);

                instance.delete_node(leafs[i].id);
            }
        }

        if (!instance.is_loaded(data.node) && !instance.is_loading(data.node)) {
            instance.load_node(data.node, cb);
        } else {
            cb.call(instance, data.node, false);
        }

        $statusbar.text(data.node.original.path);
    }).on('delete_node.jstree', function (event, data) {
        /*! select parent when delete node is selected. */
        if (data.instance.is_selected(data.node)) {
            data.instance.deselect_all();
            data.instance.select_node(data.parent);
        }
    }).on('keydown.jstree', function (event) {
        var instance = $.jstree.reference(event.target);
        if (event.altKey && 82 === event.keyCode) {
            // Alt + R, refresh node.
            instance.refresh_node(instance.get_selected());
            return false;
        }
    }).jstree({
        core: {
            check_callback: function (operation, node, parent_node, node_pos, more) {
                // allowed 'create_node' and 'delete_node' only.
                return 'create_node' === operation || 'delete_node' === operation;
            },
            err: function () {
                console.log('err', arguments);
            },
            data: function (node, cb) {
                if ('#' === node.id) {
                    // load server nodes.
                    var roots = [];
                    for (var k = 0; k < ZK_URLS.length; k++) {
                        roots.push({id: ZK_URLS[k], text: ZK_URLS[k], path: ZK_URLS[k], children: true});
                    }
                    cb.call(this, roots);
                } else {
                    var path, serverUrl, segments = [], i, pathNodes;
                    if ('#' === node.parent) {
                        path = '/';
                        serverUrl = node.original.path;
                    } else {
                        pathNodes = node.original.path.substring(1).split('/');
                        for (i = 0; i < pathNodes.length; i++) {
                            segments.push(encodeURIComponent(pathNodes[i]));
                        }
                        path = node.original.path.substring(0, 1) + segments.join('/');
                        serverUrl = node.parents[node.parents.length - 2];
                    }

                    $.ajax({
                        url: '.' + path,
                        type: 'GET',
                        dataType: 'json',
                        headers: {zkUrl: serverUrl}
                    }).done(function (data) {
                        var n, index, nodes = [], leafs = [], i;
                        for (i = 0; i < data.length; i++) {
                            n = data[i];
                            index = n.path.lastIndexOf('/');
                            n.id = serverUrl + n.path;
                            n.type = n.ephemeralOwner && 0 !== n.ephemeralOwner ? 'EPHEMERAL' : 'PERSISTENT';
                            n.text = n.path.substring(index + 1);
                            n.value = n.data;
                            n.children = 0 < n.items;

                            if (n.children) {
                                nodes.push(n);
                            } else {
                                leafs.push(n);
                            }
                            node.original[n.path] = n;
                        }
                        nodes.sort(function (n1, n2) {
                            return n1.id >= n2.id ? 1 : -1;
                        });
                        leafs.sort(function (n1, n2) {
                            return n1.id >= n2.id ? 1 : -1;
                        });
                        node.original.leafs = leafs;
                        cb(nodes);
                    });
                }
            }
        }
    });
    /* ]] nodes - nav - tree */

    /* leafs - table [[ */
    $tbody.on('dblclick', 'td.col-value', function (event) {
        var el = this, text;
        if (1 === el.childNodes.length && Node.TEXT_NODE === el.firstChild.nodeType) {
            if (!el.hasAttribute('data-snap')) {
                text = el.firstChild.textContent;
                el.setAttribute('data-snap', '(空)' !== text && text ? text : '');
            }

            var editor = el.ownerDocument.createElement('input');
            var style = window.getComputedStyle(el, null);
            editor.style.border = '1px solid #ccc';
            editor.style.outline = 'none';
            // editor.style.margin = '-1px';
            editor.style.width = (el.clientWidth - 2 - parseInt(style['padding-left']) - parseInt(style['padding-right'])) + 'px';
            /*
             editor.style.font = 'inherit';
             editor.style.lineHeight = '100%';
             */
            editor.style.verticalAlign = 'middle';
            editor.spellcheck = false;
            editor.autocomplete = 'off';
            editor.value = el.firstChild.textContent;

            el.replaceChild(editor, el.firstChild);

            function clean() {
                el.removeAttribute('data-snap');
                el.className = el.className.replace(/dirty-value/g, '');
            }

            function callback(event) {
                var snap = el.getAttribute('data-snap'), node;
                editor.value = '(空)' !== editor.value && editor.value ? editor.value : '';
                if (snap === editor.value) {
                    node = document.createTextNode(snap || '(空)');
                    clean();
                } else {
                    el.className += /dirty-value/.test(el.className) ? '' : ' dirty-value';
                    node = document.createTextNode(editor.value);
                }
                el.setAttribute('data-value', editor.value);
                el.replaceChild(node, editor);

                el.ownerDocument.body.removeEventListener('click', dispatch);
            }

            function dispatch(event) {
                var target = event.target || event.srcElement;
                if (('click' === event.type && editor !== target) || ('keydown' === event.type && 13 === event.keyCode)) {
                    // Enter
                    callback(event);
                }
            }

            editor.addEventListener('keydown', dispatch, false);
            editor.focus();
            editor.select();

            setTimeout(function () {
                el.ownerDocument.body.addEventListener('click', dispatch, false);
            });
        }
    }).on('click', 'tr', function (evt) {
        $(this).addClass('selected').focus().siblings().removeClass('selected');
        $statusbar.text($(this).attr('data-path'));
    }).on('keyup', function (event) {
        if (event.altKey && 46 === event.keyCode) {
            // Alt + Delete, Remove
            var $selected = $(this).find('tr.selected'), path = $selected.attr('data-path');
            var id = $selected.attr('data-id');
            var idx = id.lastIndexOf('/'), parent = 0 < idx ? id.substring(0, idx) : '/';
            var parentNode = $jstree.jstree().get_node(parent), i;
            var serverUrl = parentNode.parents[parentNode.parents.length - 2] || parentNode.id;

            if ($selected.is('[data-type="parent"]')) {
                if (0 < $selected.siblings().length || 0 < parentNode.children.length) {
                    // TODO remove all children first.
                    alert('请先删除子节点');
                    return;
                }
                // no children, remove the parent from table and remove parent from jstree. // TODO ajax remove
                if (window.confirm('Delete "' + path + '" ?')) {
                    $.ajax({
                        url: '.' + path,
                        type: 'DELETE',
                        dataType: 'json',
                        headers: {zkUrl: serverUrl}
                    }).done(function (data) {
                        $selected.remove();
                        $jstree.jstree().delete_node(path, function () {
                            $jstree.jstree().refresh_node(parent);
                        });
                    });
                }
            } else {
                // remove the child from table. TODO ajax remove
                if (window.confirm('Delete "' + path + '" ?')) {
                    $.ajax({
                        url: '.' + path,
                        type: 'DELETE',
                        dataType: 'json',
                        headers: {zkUrl: serverUrl}
                    }).done(function (data) {
                        if (0 >= $selected.siblings(':not([data-type=parent])').length) {
                            var pn = $jstree.jstree().get_node(parent);
                            if ($jstree.jstree().delete_node(pn)) {
                                $jstree.jstree().refresh_node(pn.parent);
                                $jstree.jstree().get_node(pn.parent, true)[0].scrollIntoView();
                            }
                            ;
                        } else {
                            $jstree.jstree().refresh_node(parent);
                        }
                        // $selected.remove();
                    });
                }
            }
        } else if (event.altKey && 45 === event.keyCode) {
            // Alt + Insert, Create
            var $selected = $(this).find('tr.selected'), path = $selected.attr('data-path');
            var id = $selected.attr('data-id');
            var index = id.lastIndexOf('/'), parent = 0 < index ? id.substring(0, index) : '/';
            parent = '/' === parent ? $selected.attr('data-root') : parent;
            var jstree = $jstree.jstree(), parentNode = jstree.get_node(parent), i;

            function callback(node) {
                var root = node.parents[node.parents.length - 2] || node.id;
                $('<tr class="dirty-row selected" data-path="' + node.path + '/new' + '" data-root="' + root + '">' +
                    '<td>' +
                    '<i class="icon icon-txt"></i>' +
                    '<span><input type="text" name="name"></span>' +
                    '</td>' +
                    '<td>' +
                    '<select name="type"><option value="0">PERSISTENT</option><option value="1">EPHEMERAL</option></select>' +
                    '</td>' +
                    '<td class="col-value"><input type="text" name="value"></td></tr>').appendTo($tbody).siblings().removeClass('selected').end().find('input[name=name]').focus();
            }

            if ($selected.is('[data-type=parent]')) {
                callback(parentNode);
            } else {
                parentNode.original[path].items = 1;
                for (i = 0; i < parentNode.children.length; i++) {
                    if (parentNode.original[path].id < parentNode.children[i]) {
                        break;
                    }
                }

                jstree.create_node(parentNode, parentNode.original[path], i, function (node) {
                    jstree.deselect_all();
                    jstree.select_node(node);
                    jstree.open_node(node, callback);
                });
            }
        }
    });
    /* ]] leafs - table */

    /* search box [[ */
    var $floatBox = $viewport.find('.float-box'),
        $expandInput = $floatBox.find('.float-box_input');

    $expandInput.on('keydown', function (event) {
        var path = this.value;
        if (13 === event.keyCode && path) {
            // Enter
            var nodes = $jstree.jstree().get_top_selected(true),
                root = nodes[0].parents[nodes[0].parents.length - 2] || nodes[0].id;
            if (root) {
                $floatBox.hide();
                event.preventDefault();
                expandTo($jstree.jstree(false), root + ('/' === path.charAt(0) ? path : '/' + path));
            }
        }
    });
    /* ]] search box */

    /* global shortcut. */
    Shortcut(document.body).all({
        'Alt + S': function (event) {
            // Alt + S, save all dirty data.
            var $td = $('.leaf-table tr td.dirty-value');
            if (0 < $td.length) {
                // TODO show loading
                $td.each(function (index, el) {
                    var path = $(el).closest('tr').attr('data-path'),
                        id = $(el).closest('tr').attr('data-id'),
                        value = el.getAttribute('data-value');
                    var idx = id.lastIndexOf('/'), parent = 0 < idx ? id.substring(0, idx) : '/';
                    var parentNode = $jstree.jstree().get_node(parent), i;
                    var serverUrl = parentNode.parents[parentNode.parents.length - 2] || parent;

                    $.ajax({
                        url: '.' + path,
                        type: 'POST',
                        dataType: 'json',
                        headers: {zkUrl: serverUrl},
                        data: {value: value}
                    }).done(function (data) {
                        var n = $jstree.jstree().get_node(path.substring(0, path.lastIndexOf('/')));
                        if (data.success) {
                            n && n.original && n.original[path] && (n.original[path].value = value);
                            el.removeAttribute('data-snap');
                            el.className = el.className.replace(/dirty-value/g, '');
                        }
                    });
                });
            }

            $('.leaf-table tr.dirty-row').each(function (index, tr) {
                var path = $(tr).siblings('[data-type=parent]').attr('data-path');
                var id = $(tr).siblings('[data-type=parent]').attr('data-id');
                var idx = id.lastIndexOf('/'), parent = 0 < idx ? id.substring(0, idx) : '/';
                parent = '/' === parent ? $(tr).attr('data-root') : parent;
                var parentNode = $jstree.jstree().get_node(parent), i;
                var serverUrl = $(tr).attr('data-root');

                var name = $(tr).find('input[name=name]').val();
                var type = $(tr).find('select[name=type]').val();
                var value = $(tr).find('input[name=value]').val();
                console.log(name, type, value)

                $.ajax({
                    url: '.' + path + '/' + name,
                    type: 'PUT',
                    dataType: 'json',
                    headers: {zkUrl: serverUrl},
                    data: {type: type, value: value}
                }).done(function (data) {
                    for (i = 0; i < parentNode.original.leafs.length; i++) {
                        if (path === parentNode.original.leafs[i].path) {
                            parentNode.original.leafs.splice(i, 1);
                            break;
                        }
                    }
                    $jstree.jstree().refresh_node(id);
                    $(tr).addClass('selected').focus().siblings().removeClass('selected');
                });
            });
            event.preventDefault();
        },
        'Alt + E': function (event) {
            // Alt + E. Search
            $floatBox.show();
            $expandInput.focus()[0].select();
            event.preventDefault();
        },
        'Esc': function (event) {
            $floatBox.hide();
            $jstree.focus();
            event.preventDefault();
        },
        'Alt + D': function (event) {
            console.log('dump');
            var node = $jstree.jstree().get_selected(true)[0], root = node.parents[node.parents.length - 2] || node.id,
                path;
            if (root) {
                path = node.original.path === root ? '/' : node.original.path;
                if (window.confirm('确定要导出 "' + path + '" ?')) {
                    window.open('.' + path + '?zkUrl=' + encodeURIComponent(root) + '&dump', '_blank')
                }
            }
            event.preventDefault();
        },
        'Alt + 1': function (event) {
            $jstree.focus();
            event.preventDefault();
        },
        'Alt + 2': function (event) {
            $('.leaf-table').focus();
            event.preventDefault();
        }
    });

    jQuery('body .statusbar > span.help').on('click', function () {
        jQuery('body .zk-ui-view .shortcut-help').toggleClass('hide')
    });
})(ZK_ALL_URLS, function () {
    /**
     * Simple shortcut manager.
     *
     * @type {Shortcut}
     */
    function Shortcut(el) {
        if (!(this instanceof Shortcut)) {
            return new Shortcut(el);
        }

        var mapping = this.__mapping = {};
        el.addEventListener('keydown', this.__delegate = function (event) {
            var modifiers = [], keyCodes = event.keyCode, queue, i;
            event.metaKey && modifiers.push('META');
            event.ctrlKey && modifiers.push('CTRL');
            event.altKey && modifiers.push('ALT');
            event.shiftKey && modifiers.push('SHIFT');
            modifiers = modifiers.join('+');
            queue = mapping[0 < modifiers.length && 'number' === typeof keyCodes ? (modifiers + '+' + keyCodes) : (0 < modifiers.length ? modifiers : keyCodes)] || [];
            for (i = 0; i < queue.length; i++) {
                queue[i].call(this, event);
            }
        });
    }

    Shortcut.prototype = {
        constructor: Shortcut,
        on: function (keys, fn) {
            var segments = keys.split(/\s*\+\s*/), i, key, modifiers = [], keyCodes = [], prop;
            for (i = 0; i < segments.length; i++) {
                key = segments[i].toUpperCase();
                if ('META' === key || 'CTRL' === key || 'ALT' === key || 'SHIFT' === key) {
                    modifiers.push(key);
                } else if ('ESC' === key) {
                    keyCodes.push(27);
                } else if (1 === key.length) {
                    keyCodes.push(key.charCodeAt(0));
                } else {
                    throw 'Illegal Key: "' + key + '"';
                }
            }
            modifiers = modifiers.sort().join('+');
            keyCodes = keyCodes.sort().join('+');
            prop = 0 < modifiers.length && 0 < keyCodes.length ? (modifiers + '+' + keyCodes) : (0 < modifiers.length ? modifiers : keyCodes);
            (this.__mapping[prop] = this.__mapping[prop] || []).push(fn);
            return this;
        },
        all: function (options) {
            var me = this;
            for (var prop in options) {
                if (!Object.prototype.hasOwnProperty.call(options, prop)) {
                    continue;
                }
                me.on(prop, options[prop]);
            }
        }
    };
    return Shortcut;
}());

