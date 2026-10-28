export const SAMPLE_DESCRIPTORS = [
  { raw: 'AMZN MKTP US*2X49L091 SEATTLE WA', expectedBrand: 'AMAZON', category: 'Shopping', amount: 1499, notes: 'Marketplace descriptor' },
  { raw: 'ACH_INB_NFLX_IN_9812', expectedBrand: 'NETFLIX', category: 'Entertainment', amount: 649, notes: 'Bank transfer with statement tokens' },
  { raw: 'S_P_O_T_I_F_Y', expectedBrand: 'SPOTIFY', category: 'Audio & Music', amount: 119, notes: 'Separated merchant letters' },
  { raw: 'RAZORPAY*HOTSTAR_PREMIUM_BLR', expectedBrand: 'DISNEY+', category: 'Entertainment', amount: 299, notes: 'Payment gateway and merchant alias' },
  { raw: 'PAYU*SWIGGYONE_MANDATE_HYD', expectedBrand: 'SWIGGY', category: 'Food & Dining', amount: 149, notes: 'Membership descriptor with payment gateway' },
  { raw: 'ZOMATO*GOLDMEMBERSHIP_GURGAON_IN', expectedBrand: 'ZOMATO', category: 'Food & Dining', amount: 99, notes: 'Membership descriptor' },
];

export const SAMPLE_REVOCATION_PRESETS = [
  { userName: 'Alex Morgan', bankName: 'HDFC Bank', merchantName: 'Adobe Creative Cloud', mandateRef: 'MND-HDFC-99281-ADOBE', rail: 'CREDIT_CARD' },
  { userName: 'Alex Morgan', bankName: 'State Bank of India', merchantName: 'Netflix', mandateRef: 'NACH-SBI-45872-NETFLIX', rail: 'NACH' },
  { userName: 'Alex Morgan', bankName: 'ICICI Bank', merchantName: 'Spotify Premium', mandateRef: 'UPI-ICICI-77391-SPOTIFY', rail: 'UPI_AUTOPAY' },
];
