package io.github.paoloboni

import cats.effect.{IO, Resource}
import cats.implicits._
import io.github.paoloboni.binance._
import io.github.paoloboni.binance.common.response._
import io.github.paoloboni.binance.common.{Interval, OrderSide, SpotConfig}
import io.github.paoloboni.binance.spot._
import io.github.paoloboni.binance.spot.parameters._

import scala.concurrent.duration.Duration
import scala.util.Random

object SpotV3E2ETests extends BaseE2ETest[SpotApi[IO]] {

  val config: SpotConfig = SpotConfig.Default(
    apiKey = sys.env("SPOT_API_KEY"),
    apiSecret = sys.env("SPOT_SECRET_KEY"),
    testnet = true,
    recvWindow = 20000
  )

  val sharedResource: Resource[IO, SpotApi[IO]] = BinanceClient.createSpotClient[IO](config)

  test("getDepth")(
    _.V3
      .getDepth(spot.parameters.v3.DepthParams("BTCUSDT", None))
      .map(succeed)
  )

  test("getPrices")(_.V3.getPrices().map(res => expect(res.nonEmpty)))

  test("getBalance")(_.V3.getBalance().map(succeed))

  test("getKLines")(
    _.V3
      .getKLines(spot.parameters.v3.KLines("BTCUSDT", Interval.`5m`, None, None, 100))
      .compile
      .toList
      .map(res => expect(res.nonEmpty))
  )

  test("createOrder") { client =>
    val side = Random.shuffle(OrderSide.values).head
    client.V3
      .createOrder(
        SpotOrderCreateParams.MARKET(
          symbol = "TRXUSDT",
          side = side,
          quantity = BigDecimal(1000).some
        )
      )
      .map(succeed)
  }

  test("queryOrder") { client =>
    val symbol = "TRXUSDT"
    for {
      createOrderResponse <- client.V3.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 1000,
          price = 0.08
        )
      )

      _ <- client.V3.queryOrder(
        SpotOrderQueryParams(
          symbol = symbol,
          orderId = createOrderResponse.orderId.some,
          origClientOrderId = None
        )
      )
    } yield success
  }

  test("cancelOrder") { client =>
    val symbol = "TRXUSDT"
    (for {
      createOrderResponse <- client.V3.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 1000,
          price = 0.08
        )
      )

      _ <- client.V3
        .cancelOrder(
          SpotOrderCancelParams(
            symbol = symbol,
            orderId = createOrderResponse.orderId.some,
            origClientOrderId = None
          )
        )
    } yield success).retryWithBackoff(initialDelay = Duration.Zero)
  }

  test("cancelAllOrders") { client =>
    val symbol = "TRXUSDT"
    (for {
      _ <- client.V3.createOrder(
        SpotOrderCreateParams.LIMIT(
          symbol = symbol,
          side = OrderSide.SELL,
          timeInForce = SpotTimeInForce.GTC,
          quantity = 1000,
          price = 0.08
        )
      )

      _ <- client.V3
        .cancelAllOrders(
          SpotOrderCancelAllParams(symbol = symbol)
        )
    } yield success).retryWithBackoff(initialDelay = Duration.Zero)
  }

  test("tradeStreams")(
    _.tradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  )

  test("kLineStreams")(
    _.kLineStreams("btcusdt", Interval.`1m`)
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  )

  test("diffDepthStream")(
    _.diffDepthStream("btcusdt")
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  )

  test("partialBookDepthStream")(
    _.partialBookDepthStream("btcusdt", Level.`5`)
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  )

  test("allBookTickersStream")(
    _.allBookTickersStream()
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  )

  test("aggregateTradeStreams")(
    _.aggregateTradeStreams("btcusdt")
      .take(1)
      .compile
      .toList
      .map(l => expect(l.size == 1))
  )

}
