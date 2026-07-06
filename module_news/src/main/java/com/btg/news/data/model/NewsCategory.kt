package com.btg.news.data.model

/** 聚合数据新闻分类。type 为接口参数值，label 为 Tab 展示文案。 */
enum class NewsCategory(val type: String, val label: String) {
    TOP("top", "推荐"),
    GUONEI("guonei", "国内"),
    GUOJI("guoji", "国际"),
    YULE("yule", "娱乐"),
    TIYU("tiyu", "体育"),
    KEJI("keji", "科技"),
    CAIJING("caijing", "财经"),
    YOUXI("youxi", "游戏"),
    QICHE("qiche", "汽车"),
    JIANKANG("jiankang", "健康"),
}
