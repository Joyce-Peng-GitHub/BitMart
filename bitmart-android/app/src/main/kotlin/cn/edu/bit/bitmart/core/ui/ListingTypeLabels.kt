package cn.edu.bit.bitmart.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.model.ListingType

/**
 * 列表类型相关的本地化标签集中在此，供买卖列表、"我的"列表、详情、交易等处复用，
 * 保证 SELL/BUY 文案在各页面完全一致。所有扩展返回当前 locale 解析后的字符串。
 */

/** 类型标题：卖品 = 商品，求购 = 收购。 */
@Composable
fun ListingType.titleLabel(): String = stringResource(
    if (this == ListingType.BUY) R.string.listing_type_title_buy else R.string.listing_type_title_sell,
)

/** 价格标签：卖品 = 售价，求购 = 期望价。 */
@Composable
fun ListingType.priceLabel(): String = stringResource(
    if (this == ListingType.BUY) R.string.listing_price_label_buy else R.string.listing_price_label_sell,
)

/** 成交动词（数量前缀）：卖品 = 已售，求购 = 已收。 */
@Composable
fun ListingType.soldVerbLabel(): String = stringResource(
    if (this == ListingType.BUY) R.string.listing_sold_verb_buy else R.string.listing_sold_verb_sell,
)

/** 成交满额提示：卖品 = 售罄，求购 = 已收满。 */
@Composable
fun ListingType.soldOutLabel(): String = stringResource(
    if (this == ListingType.BUY) R.string.listing_sold_out_buy else R.string.listing_sold_out_sell,
)

/** 成交数量名词：卖品 = 已售出数量，求购 = 已收购数量。 */
@Composable
fun ListingType.soldQuantityLabel(): String = stringResource(
    if (this == ListingType.BUY) R.string.listing_sold_quantity_buy else R.string.listing_sold_quantity_sell,
)
