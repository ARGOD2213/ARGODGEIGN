import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

const PlantScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Plant Dashboard</Text>
      <Text>Plant overview here.</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  title: { fontSize: 24 },
});

export default PlantScreen;