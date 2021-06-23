import { requireNativeComponent, ViewStyle } from 'react-native';

type ScannerProps = {
  color: string;
  style: ViewStyle;
};

export const ScannerViewManager = requireNativeComponent<ScannerProps>(
'ScannerView'
);

export default ScannerViewManager;
