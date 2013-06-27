function test() {
    var a = kotlin.arrayFromFun(3, function () {
        return 3
    });
    return (a[0] == 3) && (a[2] == 3) && (a[1] == 3);
}