grpcurl -d '{"cartId":"cart3", "itemId":"hat", "quantity":3}' -plaintext 127.0.0.1:8103 shopping.cart.ShoppingCartService.AddItem
grpcurl -d '{"cartId":"cart0"}' -plaintext 127.0.0.1:8103 shopping.cart.ShoppingCartService.Get
grpcurl -d '{"itemId":"Hoodies"}' -plaintext 127.0.0.1:8101 shopping.cart.ShoppingCartService.GetItemPopularity
grpcurl -d '{"cartId":"cart1", "itemId":"Hoodies", "quantity":6}' -plaintext 127.0.0.1:8102 shopping.cart.ShoppingCartService.AddItem
grpcurl -d '{"itemId":"hat"}' -plaintext 127.0.0.1:8101 shopping.cart.ShoppingCartService.GetItemPopularity

